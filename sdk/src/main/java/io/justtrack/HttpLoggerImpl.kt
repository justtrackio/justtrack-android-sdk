package io.justtrack

import io.justtrack.exceptions.AwaitingIdException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.versions.VersionBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.util.Date
import java.util.UUID

internal class HttpLoggerImpl internal constructor(
    override val fallback: Logger,
    val httpClient: HttpClient,
    private val versionBundle: VersionBundle,
    private val logAggregator: LogAggregator,
) : HttpLogger {
    private var advertiserId: String? = null
    private var logMessageRules: DTOAttributionOutputSdkLog? = null
    private var metricRules: DTOAttributionOutputSdkMetric? = null
    private var breadCrumbReporter: BreadCrumbReporter? = null

    private var userId: String? = null
    private var installInstanceId: String? = null

    override fun setBreadCrumbReporter(reporter: BreadCrumbReporter?) {
        this.breadCrumbReporter = reporter
    }

    override fun debug(message: String, vararg fields: LoggerFields) {
        fallback.debug(message, *fields)
        // Debug logs are no longer sent to the backend.
    }

    override fun info(message: String, vararg fields: LoggerFields) {
        fallback.info(message, *fields)
        writeLog(LogLevel.INFO, message, null, *fields)
    }

    override fun warn(message: String, vararg fields: LoggerFields) {
        fallback.warn(message, *fields)

        writeLog(LogLevel.WARN, message, null, *fields)
        publishMetric(WARNING_METRIC, 1.0)
    }

    override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
        fallback.warn(message, exception, *fields)

        writeLog(LogLevel.WARN, message, exception, *fields)
        publishMetric(WARNING_METRIC, 1.0)
    }

    override fun error(message: String, vararg fields: LoggerFields) {
        fallback.error(message, *fields)

        writeLog(LogLevel.ERROR, message, null, *fields)
        publishMetric(ERROR_METRIC, 1.0)
    }

    override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) {
        fallback.error(message, *fields)

        writeLog(LogLevel.ERROR, message, exception, *fields)
        publishMetric(ERROR_METRIC, 1.0)
    }

    override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) {
        fallback.publishMetric(metric, value, *dimensions)
        writeMetric(metric, value, dimensions)
    }

    override fun setAdvertiserId(advertiserId: String) {
        this.advertiserId = advertiserId
        if (fallback is HttpLogger) {
            fallback.setAdvertiserId(advertiserId)
        }
    }

    override fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>) {
        CoroutineScope(Dispatchers.IO).launch {
            val newUserId = userId.await()
            if (newUserId != null) {
                this@HttpLoggerImpl.userId = newUserId.toString()
            }

            this@HttpLoggerImpl.installInstanceId = installInstanceId.await().toString()
            if (fallback is HttpLogger) {
                fallback.setUser(userId, installInstanceId)
            }
        }
    }

    override fun setUser(userId: UUID?, installId: String) {
        if (userId != null) {
            this@HttpLoggerImpl.userId = userId.toString()
        }

        this@HttpLoggerImpl.installInstanceId = installId
        if (fallback is HttpLogger) {
            fallback.setUser(userId, installId)
        }
    }

    override fun setLogAndMetricRules(config: DTOAttributionOutputSdkConfig) {
        this.logMessageRules = config.log
        this.metricRules = config.metric
    }

    @Throws(Exception::class)
    override fun close() {
        logAggregator.close()
    }

    override fun sendToServer() {
        logAggregator.sendLogsAndMetrics(
            object : LogAggregator.LogSender {
                override suspend fun sendLogsAndMetrics(messages: Collection<DTOLogMessage>, metrics: Collection<DTOLogMetric>): Result<Unit> {
                    return sendToServer(messages, metrics)
                }
            },
        )
    }

    private suspend fun sendToServer(messages: Collection<DTOLogMessage>, metrics: Collection<DTOLogMetric>): Result<Unit> {
        if (userId == null && this@HttpLoggerImpl.installInstanceId == null) {
            return Result.failure(AwaitingIdException())
        }
        val userId = userId
        val installInstanceId = this@HttpLoggerImpl.installInstanceId

        val sdkVersion = versionBundle.sdkVersion
        val metricRules = this.metricRules
        val reducedMessages = EventLimiter.filterLogMessages(messages, logMessageRules, fallback)

        val reduceMetric: Collection<DTOLogMetric> = if (metricRules != null) {
            EventLimiter.filterByRules(
                metrics,
                metricRules.rules,
                fallback,
            ) { obj: DTOLogMetric -> LogMetricDatum(obj) }
        } else {
            metrics
        }

        if (reducedMessages.isEmpty() && reduceMetric.isEmpty()) {
            // no logs or metrics left to send, we are done
            return Result.success(Unit)
        }

        val body: JSONEncodable = DTOLogInput(
            reducedMessages,
            reduceMetric,
            DTOAppVersion(versionBundle.applicationVersion.getVersionName(), versionBundle.applicationVersion.getVersionCode()),
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

        val result = httpClient.sendLogs(
            this@HttpLoggerImpl,
            body,
            advertiserId,
            userId,
            installInstanceId,
        )

        if (result.isSuccess) {
            fallback.debug("Published " + messages.size + " log messages and " + metrics.size + " metrics")
        } else {
            fallback.warn(
                "Failed to publish " + messages.size + " log messages and " + metrics.size + " metrics",
                result.exceptionOrNull() ?: Throwable("No Exception"),
            )
        }

        return result
    }

    private fun writeLog(level: LogLevel, message: String, exception: Throwable?, vararg fields: LoggerFields) {
        try {
            val encodedFields = JSONObject()
            fields.forEach { loggerFields ->
                addFields(encodedFields, loggerFields)
            }

            if (exception != null) {
                addFields(encodedFields, LoggerFieldsBuilder().with("exception", exception))
            }
            logAggregator.addLogMessage(LogMessageDatum(level, message, encodedFields, Date()))
            breadCrumbReporter?.addBreadCrumb(
                BreadCrumb(
                    message,
                    "logs",
                    level,
                    Date(),
                ),
            )
        } catch (writeLogException: Throwable) {
            fallback.warn("Failed to send logs to backend", writeLogException)
        }
    }

    private fun writeMetric(metric: Metric, value: Double, fields: Array<out LoggerFields>?) {
        try {
            val encodedFields = JSONObject()
            addFields(encodedFields, metric)

            fields?.forEach { loggerFields ->
                addFields(encodedFields, loggerFields)
            }

            logAggregator.addLogMetric(DTOLogMetric(metric.metric, encodedFields, value, metric.unit.unit, Date()))
            breadCrumbReporter?.addBreadCrumb(
                BreadCrumb(
                    metric.metric,
                    "metrics",
                    LogLevel.DEBUG,
                    Date(),
                ),
            )
        } catch (writeMetricException: Throwable) {
            fallback.warn("Failed to send logs to backend", writeMetricException)
        }
    }

    @Throws(JSONException::class)
    private fun addFields(encodedFields: JSONObject, fields: LoggerFields) {
        for ((key, value) in fields.fields) {
            encodedFields.put(key, value)
        }
    }
    companion object {
        internal val ERROR_METRIC = Metric("Errors")
        internal val WARNING_METRIC = Metric("Warnings")
    }
}
