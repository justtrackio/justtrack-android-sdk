package io.justtrack

import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.google.android.gms.appset.AppSetIdInfo
import io.justtrack.api.AttributionApi
import io.justtrack.api.AttributionApiImpl
import io.justtrack.executor.TaskExecutor
import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.retargeting.RetargetingParametersListener
import io.justtrack.util.InstallerSourceIdProvider
import io.justtrack.versions.VersionBundle
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * Class to encapsulate getting an [AttributionOutput] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class AttributionOutputProviderImpl internal constructor(
    private val attributionApi: AttributionApi,
    private val taskExecutor: TaskExecutor,
    private val databaseInterface: DatabaseInterface,
    private val attributionParams: AttributionParams,
    private val subscribers: Subscribers,
    private val loggers: Loggers,
    private val versionBundle: VersionBundle,
) : AttributionOutputProvider {
    private val attributionHandler = AttributionHandlerImpl(
        taskExecutor,
        AttributionHandlerImpl.AttributionParams(
            this,
            attributionParams.attributionIdManager,
            attributionParams.userIdProvider,
            attributionParams.advertiserIdProvider,
        ),
        AttributionHandlerImpl.Loggers(loggers.httpLogger, loggers.networkErrorLogger),
        AttributionHandlerImpl.Subscribers(
            subscribers.attributionSubscriptionHandler,
            subscribers.retargetingParametersSubscriptions,
        ),
        attributionParams.preliminaryRetargetingParametersImpl,
    )
    private var attributionOutput: AsyncFuture<AttributionOutput>? = null
    private var attributionCanRetryAt: Long = 0L

    private var referrerDetails: AsyncFuture<ReferrerDetails?>? = null

    // Do not use this variable, this is only for checking if the attribution request that failed is the most recent one or not.
    private var latestApiAttributionOutput: AsyncFuture<AttributionOutput>? = null

    @Synchronized
    override fun setOutput(output: AsyncFuture<AttributionOutput>?) {
        attributionOutput = output
        latestApiAttributionOutput = output
    }

    @Synchronized
    override fun getOutput(): AsyncFuture<AttributionOutput>? {
        return attributionOutput
    }

    @Synchronized
    override fun setAttributionCanRetryAt(value: Long) {
        this.attributionCanRetryAt = value
    }

    @Synchronized
    override fun getLatestApiAttributionOutput(): AsyncFuture<AttributionOutput>? {
        return latestApiAttributionOutput
    }

    override fun getReFetchReAttributionDelaySeconds(): Long {
        return attributionParams.reFetchReAttributionDelaySeconds
    }

    override fun getAttributionRetryDelaySeconds(): Long {
        return attributionParams.attributionRetryDelaySeconds
    }

    @Synchronized
    override fun provideAttributionOutput(forcedDecision: AttributionDecision?): AsyncFuture<AttributionOutput> {
        val currentOutput = attributionOutput

        // for the common case that we just need to get the current attribution future, check if we already
        if (forcedDecision == null) {
            // Deadlock-Safety: We just check if we have a ready attribution
            synchronized(this) {
                currentOutput?.let {
                    if (!attributionIsOldFail(currentOutput)) {
                        return it
                    }
                }
            }
        }

        val result = taskExecutor.executeFuture(
            getAttributionOutputTask(
                attributionOutput,
                forcedDecision,
                attributionParams.advertiserIdProvider,
                attributionParams.appSetIdFuture.invoke(),
                attributionParams.trackingId,
                attributionParams.trackingProvider,
                attributionParams.integritySecretFuture.invoke(),
            ),
        )
        attributionOutput = result
        return result
    }

    @VisibleForTesting
    internal fun attributionIsOldFail(attributionOutput: AsyncFuture<AttributionOutput>?): Boolean {
        return attributionCanRetryAt != 0L && attributionOutput is ErrorFuture<*> && attributionCanRetryAt < System.currentTimeMillis()
    }

    @VisibleForTesting
    internal fun getAttributionOutputTask(
        attributionOutput: AsyncFuture<AttributionOutput>?,
        forcedDecision: AttributionDecision?,
        advertiserIdProvider: AdvertiserIdProvider,
        appSetIdFuture: AsyncFuture<AppSetIdInfo?>,
        trackingId: String?,
        trackingProvider: String,
        integritySecretFuture: AsyncFuture<String>,
    ) = object : Task<AttributionOutput> {
        override suspend fun execute(): AttributionOutput {
            var currentOutput: AsyncFuture<AttributionOutput>? = attributionOutput

            var attributionTimestamps: AttributionTimestamps?
            var storedOutput: AttributionOutput? = null

            databaseInterface.openAttribution().use {
                attributionTimestamps = it.getAttributionTimestamps()
                it.setLastOpen(System.currentTimeMillis())

                try {
                    storedOutput = it.getStoredOutput()
                } catch (exception: Throwable) {
                    loggers.httpLogger.warn("Failed to parse stored response data", exception)
                }
            }

            val attributionDecision: AttributionDecision
            val attributionFuture: AsyncFuture<AttributionOutput>?

            // Deadlock-Safety: We should only read and write fields of this instance. We also construct
            // an attribution request as needed, but we don't call any untrusted code.
            synchronized(this) {
                currentOutput?.let {
                    if (forcedDecision == null) {
                        if (attributionIsOldFail(currentOutput)) {
                            attributionCanRetryAt = 0
                            currentOutput = null
                            loggers.httpLogger.debug("Retrying old failed attribution")
                        } else {
                            return it.get()
                        }
                    }
                }

                attributionDecision = forcedDecision
                    ?: attributionParams.reAttributionDecider.needsReAttribution(attributionTimestamps)

                val cachedOutput = storedOutput
                if (!attributionDecision.shouldFetchAttribution && cachedOutput != null) {
                    currentOutput = ValueFuture(cachedOutput)
                    attributionFuture = null
                    latestApiAttributionOutput = null
                } else {
                    val referrerDetailsTask: AsyncFuture<ReferrerDetails?> = if (attributionDecision.shouldUseReferrerDetails(storedOutput != null)) {
                        val refDetails = referrerDetails
                        if (refDetails != null) {
                            refDetails
                        } else {
                            val temp = taskExecutor.executeFuture(
                                attributionParams.installReferrerProvider.newTask(),
                            )
                            referrerDetails = temp
                            temp
                        }
                    } else {
                        ValueFuture(null)
                    }

                    var task: Task<AttributionOutput> = AttributionTask(
                        attributionParams.intent,
                        databaseInterface,
                        AttributionTask.AttributionParams(
                            attributionParams.idManager,
                            attributionParams.userIdProvider,
                            advertiserIdProvider.provideAdvertiserId(),
                            referrerDetailsTask,
                            appSetIdFuture,
                            trackingId,
                            trackingProvider,
                            attributionParams.claimProvider,
                            forcedDecision?.claimsTimeout ?: ClaimProviderImpl.CLAIM_TIMEOUT_FAST_MS,
                            attributionParams.sdkConfig,
                            attributionParams.deviceInfo,
                            integritySecretFuture,
                        ),
                        attributionApi,
                        loggers.httpLogger,
                        versionBundle,
                        attributionParams.installerSourceIdProvider,
                    )
                    task = RetryingTask(
                        task,
                        attributionParams.deviceInfo,
                        loggers.httpLogger,
                        attributionParams.retryConfig.attributionRequestRetries,
                        AttributionErrorClassifier.getInstance(),
                        AttributionApiImpl.GET_ATTRIBUTION_REQUEST_NAME,
                    )
                    currentOutput = taskExecutor.executeFuture(task)
                    latestApiAttributionOutput = currentOutput
                    attributionFuture = currentOutput
                }
            }

            if (attributionFuture == null) {
                val output = storedOutput
                if (output != null) {
                    val storedResponse = output.getAttributionResponse()
                    loggers.httpLogger.setUser(storedResponse.getUserId(), storedResponse.getInstallId())
                    loggers.httpLogger.debug("Using cached attribution")
                    subscribers.attributionSubscriptionHandler.callAttributionSubscriptions(storedResponse)
                } else {
                    loggers.httpLogger.debug("Using cached attribution, storedOutput is null")
                }
                return currentOutput?.await()
                    ?: error("Cached attribution output future is missing")
            }

            val storedResponse: AttributionResponse? = storedOutput?.getAttributionResponse()

            try {
                attributionHandler.handleAttribution(
                    attributionFuture,
                    storedResponse,
                    attributionTimestamps,
                    forcedDecision,
                    attributionDecision,
                )
            } catch (exception: Exception) {
                loggers.networkErrorLogger.logException(
                    loggers.httpLogger,
                    exception,
                    "Failed to perform attribution, SDK is shutting down",
                )
            }

            return try {
                attributionFuture.await()
            } catch (exception: ExecutionException) {
                throw exception.cause ?: exception
            }
        }
    }

    internal data class Loggers(
        internal val httpLogger: HttpLogger,
        internal val networkErrorLogger: NetworkErrorLogger,
    )

    internal data class AttributionParams(
        internal val idManager: AttributionIdManager,
        internal val userIdProvider: UserIdProvider,
        internal val reAttributionDecider: ReAttributionDecider,
        internal val installReferrerProvider: InstallReferrerProvider,
        internal val claimProvider: ClaimProvider,
        internal val retryConfig: RetryConfig,
        internal val trackingId: String?,
        internal val trackingProvider: String,
        internal val advertiserIdProvider: AdvertiserIdProvider,
        internal val appSetIdFuture: () -> AsyncFuture<AppSetIdInfo?>,
        internal val integritySecretFuture: () -> AsyncFuture<String>,
        internal val reFetchReAttributionDelaySeconds: Long,
        internal val attributionRetryDelaySeconds: Long,
        internal val sdkConfig: JustTrackSdkConfig,
        internal val deviceInfo: DeviceInfo,
        internal val intent: Intent?,
        internal val attributionIdManager: AttributionIdManager,
        internal val installerSourceIdProvider: InstallerSourceIdProvider,
        internal val preliminaryRetargetingParametersImpl: PreliminaryRetargetingParametersImpl?,
    )

    internal data class Subscribers(
        internal val attributionSubscriptionHandler: AttributionSubscriptionHandler,
        internal val retargetingParametersSubscriptions: SubscriptionManager<RetargetingParametersListener>,
    )
}
