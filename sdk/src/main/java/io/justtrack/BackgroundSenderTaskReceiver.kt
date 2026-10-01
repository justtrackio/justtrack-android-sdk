package io.justtrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import io.justtrack.api.EventApi
import io.justtrack.api.EventApiImpl
import io.justtrack.api.HeaderProviderImpl
import io.justtrack.api.LogApi
import io.justtrack.api.LogApiImpl
import io.justtrack.dtos.DTOAppVersion
import io.justtrack.dtos.DTOLogInput
import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.DTOLogMetric
import io.justtrack.dtos.DTOSdkVersion
import io.justtrack.log.CompositeLogger
import io.justtrack.log.Logger
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.VersionBundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal open class BackgroundSenderTaskReceiver : BroadcastReceiver() {
    private var mHttpClient: HttpClient? = null
    private lateinit var eventApi: EventApi
    private lateinit var logApi: LogApi
    private val formatter = Formatter

    @VisibleForTesting
    val resultForTest = CompletableDeferred<Boolean>()

    override fun onReceive(context: Context?, intent: Intent?) {
        val pendingResult = goAsync()
        if (context == null || intent?.extras == null) {
            resultForTest.complete(false)
            pendingResult.finish()
            return
        }
        val inputData: WorkerInputData = getWorkerInputData(intent)
        val deviceInfo = DeviceInfoImpl(context)
        val applicationVersion = ApplicationVersionImpl(inputData.applicationPackageName, inputData.applicationVersion.getVersionCode())
        val versionBundle = VersionBundle(inputData.sdkVersion, applicationVersion)
        val defaultLogger: Logger = LoggerImpl(inputData.isLogEnabled)

        val headerProvider = HeaderProviderImpl(
            inputData.apiToken,
            inputData.applicationPackageName,
            deviceInfo,
            versionBundle,
        )
        val fallbackLogger = CompositeLogger(defaultLogger, null)
        val httpClient: HttpClient = getHttpClient(deviceInfo, defaultLogger)
        if (!::eventApi.isInitialized) eventApi = EventApiImpl(httpClient, headerProvider, inputData.env, defaultLogger)
        if (!::logApi.isInitialized) logApi = LogApiImpl(httpClient, headerProvider, inputData.env)

        val databaseInterfaces = createDatabaseInterfaces(context, fallbackLogger)
        val networkErrorLogger = NetworkErrorLogger()

        val logger = createHttpLogger(
            databaseInterfaces,
            fallbackLogger,
            networkErrorLogger,
            versionBundle,
        ).apply {
            setUser(inputData.userId, inputData.installId)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(TIMEOUT) {
                    performBackgroundTask(
                        context,
                        deviceInfo,
                        databaseInterfaces,
                        inputData,
                        logger,
                        networkErrorLogger,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                logger.info("Background service timed out")
            } finally {
                databaseInterfaces.metricDB.close()
                databaseInterfaces.messageDB.close()
                databaseInterfaces.eventDB.close()
                pendingResult?.finish()
            }
        }
    }

    private suspend fun performBackgroundTask(
        context: Context,
        deviceInfo: DeviceInfo,
        databaseInterfaces: DatabaseInterfaces,
        inputData: WorkerInputData,
        logger: Logger,
        networkErrorLogger: NetworkErrorLogger,
    ) {
        try {
            val sessionStore =
                context.getSharedPreferences(
                    SessionManagerImpl.SESSION_PREF_NAME,
                    Context.MODE_PRIVATE,
                )

            storePendingSessionEndEvent(inputData, databaseInterfaces.eventDB, logger, sessionStore)

            val result = sendData(
                deviceInfo,
                logger,
                databaseInterfaces,
                inputData,
                networkErrorLogger,
            )
            resultForTest.complete(result)
        } catch (e: java.lang.Exception) {
            networkErrorLogger.logException(logger, e, "BackgroundSenderTaskReceiver, Failed to send data to backend")
            resultForTest.complete(false)
        }
    }

    private suspend fun sendData(
        deviceInfo: DeviceInfo,
        logger: Logger,
        databaseInterfaces: DatabaseInterfaces,
        inputData: WorkerInputData,
        networkErrorLogger: NetworkErrorLogger,
    ): Boolean {
        try {
            val eventResult = sendAllEvents(deviceInfo, logger, databaseInterfaces.eventDB, inputData)
            val messageAndMetricResult = sendMessagesAndMetrics(
                logger,
                databaseInterfaces.messageDB,
                databaseInterfaces.metricDB,
                inputData,
            )

            return eventResult && messageAndMetricResult
        } catch (error: Exception) {
            networkErrorLogger.logException(logger, error, "BackgroundSenderTaskReceiver, Failed to send data to backend")
        }
        return false
    }

    private suspend fun sendAllEvents(deviceInfo: DeviceInfo, logger: Logger, eventDB: DatabaseEventInterface, inputData: WorkerInputData): Boolean {
        val eventGroupBaseOnVersion = eventDB.getAllEvent().groupBy {
            it.sdkVersionName
        }
        val events = eventGroupBaseOnVersion.values.flatten().chunked(BATCH_SIZE)

        events.forEach { chunk ->
            if (chunk.isNotEmpty()) {
                val sendEventResult =
                    sendChunkedEvents(
                        deviceInfo,
                        logger,
                        chunk,
                        inputData,
                    )
                if (!sendEventResult) {
                    return false
                }

                chunk.forEach { event ->
                    logger.debug(
                        "BackgroundSenderTaskReceiver: Successfully published seqNo ${event.sequenceNumber}, $event at ${event.timestamp} in batch",
                    )
                }

                eventDB.deleteByIdEvent(chunk.map { it.id })
            }
        }

        return true
    }

    private fun getPreviousSessionEndEvent(inputData: WorkerInputData, preferences: SharedPreferences): UserEventEntity? {
        val lastSession = SessionManagerImpl.Session.getCurrentSession(preferences)
        return if (lastSession == null) {
            null
        } else {
            val storableEndEvent = StorableEvent(
                eventId = UUID.randomUUID(),
                event = lastSession.end().build("", inputData.sdkVersion),
            )

            UserEventEntity(storableEndEvent, Formatter)
        }
    }

    private suspend fun storePendingSessionEndEvent(
        inputData: WorkerInputData,
        eventDatabase: DatabaseEventInterface,
        logger: Logger,
        preferences: SharedPreferences,
    ) {
        try {
            getPreviousSessionEndEvent(inputData, preferences)?.let { endEvent ->
                eventDatabase.insertEvent(endEvent)
                SessionManagerImpl.Session.remove(preferences)
            }
        } catch (exception: Exception) {
            logger.warn("BackgroundSenderTaskReceiver, unable to store pending SESSION_END_EVENT", exception)
        }
    }

    private suspend fun sendChunkedEvents(
        deviceInfo: DeviceInfo,
        logger: Logger,
        events: List<UserEventEntity>,
        inputData: WorkerInputData,
    ): Boolean {
        val publishingEvents = events.map {
            it.transform(formatter, logger, inputData.sdkVersion.platformType)
        }

        val dtoUserEvent = PublishEventsQueue.build(
            publishingEvents,
            deviceInfo,
            PublishEventsQueue.DTOBuildAttributionParams(
                inputData.advertiseId,
                inputData.trackingId,
                inputData.trackingProvider,
                inputData.userId,
                UUID.fromString(inputData.installInstanceId),
            ),
            inputData.sdkVersion,
            inputData.applicationVersion,
        )
        val result = eventApi.sendUserEvents(
            dtoUserEvent,
            inputData.advertiseId,
            inputData.userId.toString(),
            inputData.installId,
        )

        return result.isSuccess
    }

    private suspend fun sendMessagesAndMetrics(
        logger: Logger,
        messageDB: DatabaseMessageInterface,
        metricDB: DatabaseMetricInterface,
        inputData: WorkerInputData,
    ): Boolean {
        val messages = messageDB.getAllMessage().map {
            it.transform(formatter, logger)
        }.chunked(BATCH_SIZE)

        val metrics = metricDB.getAllMetric().map {
            it.transform(formatter, logger)
        }.chunked(BATCH_SIZE)

        for (i in 0 until max(messages.size, metrics.size)) {
            val messageChunk = messages.getOrNull(i) ?: Collections.emptyList()
            val metricChunk = metrics.getOrNull(i) ?: Collections.emptyList()

            val result =
                sendChunkedLogs(
                    logger,
                    messageChunk,
                    metricChunk,
                    inputData,
                )

            if (!result) {
                return false
            }

            if (messageChunk.isNotEmpty()) {
                messageDB.deleteByIdMessage(messageChunk.map { it.id })
            }

            if (metricChunk.isNotEmpty()) {
                metricDB.deleteByIdMetric(metricChunk.map { it.id })
            }
        }

        return true
    }

    private suspend fun sendChunkedLogs(
        logger: Logger,
        messages: Iterable<DTOLogMessage>,
        metrics: Collection<DTOLogMetric>,
        inputData: WorkerInputData,
    ): Boolean {
        val appVersion: ApplicationVersion = inputData.applicationVersion
        val sdkVersion: SdkVersion = inputData.sdkVersion

        val body: JSONEncodable = DTOLogInput(
            messages,
            metrics,
            DTOAppVersion(appVersion.getVersionName(), appVersion.getVersionCode()),
            DTOSdkVersion(
                sdkVersion.major,
                sdkVersion.minor,
                sdkVersion.patch,
                sdkVersion.name,
                sdkVersion.platformType.platform,
                sdkVersion.platformType.wrapper,
            ),
            Date(),
        )
        val result = logApi.sendLogs(
            logger,
            body,
            inputData.advertiseId,
            inputData.userId.toString(),
            inputData.installId,
        )

        return result.isSuccess
    }

    private fun getWorkerInputData(intent: Intent): WorkerInputData {
        return WorkerInputData(intent)
    }

    private fun createHttpLogger(
        databaseInterfaces: DatabaseInterfaces,
        fallback: Logger,
        networkErrorLogger: NetworkErrorLogger,
        versionBundle: VersionBundle,
    ): HttpLoggerImpl {
        val logAggregator = LogAggregatorImpl(
            MessageRepositoryImpl(formatter, databaseInterfaces.messageDB, fallback),
            MetricRepositoryImpl(formatter, databaseInterfaces.metricDB, fallback),
            fallback,
            networkErrorLogger,
            AtomicBoolean(true),
        )
        return HttpLoggerImpl(
            fallback,
            logApi,
            versionBundle,
            logAggregator,
        )
    }

    @VisibleForTesting
    fun setHttpClient(injectedHttpClient: HttpClient) {
        this.mHttpClient = injectedHttpClient
    }

    @VisibleForTesting
    fun setEventApi(injectedEventApi: EventApi) {
        this.eventApi = injectedEventApi
    }

    @VisibleForTesting
    fun setLogApi(injectedLogApi: LogApi) {
        this.logApi = injectedLogApi
    }

    private fun getHttpClient(deviceInfo: DeviceInfo, consoleLogger: Logger): HttpClient {
        if (mHttpClient != null) {
            return mHttpClient as HttpClient
        }

        val httpClient = HttpClientImpl(
            deviceInfo,
            consoleLogger,
        )
        mHttpClient = httpClient

        return httpClient
    }

    private fun createDatabaseInterfaces(context: Context, fallbackLogger: Logger): DatabaseInterfaces {
        val databaseInterface = DatabaseInterface.getInstance(context, fallbackLogger)
        val messageDatabaseInterface = databaseInterface.openMessages()
        val metricDatabaseInterface = databaseInterface.openMetrics()
        val eventDatabaseInterface = databaseInterface.openEvents()

        return DatabaseInterfaces(
            eventDatabaseInterface,
            messageDatabaseInterface,
            metricDatabaseInterface,
        )
    }

    internal data class DatabaseInterfaces(
        internal val eventDB: DatabaseEventInterface,
        internal val messageDB: DatabaseMessageInterface,
        internal val metricDB: DatabaseMetricInterface,
    )

    private companion object {
        private const val BATCH_SIZE = 100
        private const val TIMEOUT = 5_000L
    }
}
