package io.justtrack

import android.content.Intent
import com.google.android.gms.appset.AppSetIdInfo
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.versions.VersionBundle
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * Class to encapsulate getting an [AttributionOutput] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class AttributionOutputProvider internal constructor(
    private val sdk: JustTrackSdkImpl,
    private val deviceInfo: DeviceInfo,
    private val taskExecutor: TaskExecutor,
    private val databaseInterface: DatabaseInterface,
    private val attributionParams: AttributionParams,
    private val loggers: Loggers,
    private val versionBundle: VersionBundle,
) {
    private var attributionOutput: AsyncFuture<AttributionOutput?>? = null
    private var attributionCanRetryAt: Long = 0L

    private var referrerDetails: AsyncFuture<ReferrerDetails?>? = null

    // Do not use this variable, this is only for checking if the attribution request that failed is the most recent one or not.
    private var latestApiAttributionOutput: AsyncFuture<AttributionOutput?>? = null

    @Synchronized
    @JvmName("setOutput")
    internal fun setOutput(output: AsyncFuture<AttributionOutput?>?) {
        attributionOutput = output
        latestApiAttributionOutput = output
    }

    @Synchronized
    @JvmName("getOutput")
    internal fun getOutput(): AsyncFuture<AttributionOutput?>? {
        return attributionOutput
    }

    @Synchronized
    @JvmName("setAttributionCanRetryAt")
    internal fun setAttributionCanRetryAt(value: Long) {
        this.attributionCanRetryAt = value
    }

    @Synchronized
    @JvmName("getLatestApiAttributionOutput")
    internal fun getLatestApiAttributionOutput(): AsyncFuture<AttributionOutput?>? {
        return latestApiAttributionOutput
    }

    @JvmName("getReFetchReAttributionDelaySeconds")
    internal fun getReFetchReAttributionDelaySeconds(): Long {
        return attributionParams.reFetchReAttributionDelaySeconds
    }

    @JvmName("getAttributionRetryDelaySeconds")
    internal fun getAttributionRetryDelaySeconds(): Long {
        return attributionParams.attributionRetryDelaySeconds
    }

    @Synchronized
    @JvmName("provideAttributionOutput")
    internal fun provideAttributionOutput(
        forcedDecision: AttributionDecision? = null,
        advertiserIdFuture: AsyncFuture<AdvertiserIdInfo>,
        appSetIdFuture: AsyncFuture<AppSetIdInfo?>,
        trackingId: String?,
        trackingProvider: String,
        integritySecretFuture: AsyncFuture<String>,
    ): AsyncFuture<AttributionOutput?> {
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

        val result = taskExecutor.executeAsFuture(
            getAttributionOutputTask(
                attributionOutput,
                forcedDecision,
                advertiserIdFuture,
                appSetIdFuture,
                trackingId,
                trackingProvider,
                integritySecretFuture,
            ),
        )
        attributionOutput = result
        return result
    }

    private fun attributionIsOldFail(attributionOutput: AsyncFuture<AttributionOutput?>?): Boolean {
        return attributionCanRetryAt != 0L && attributionOutput is ErrorFuture<*> && attributionCanRetryAt < System.currentTimeMillis()
    }

    private fun getAttributionOutputTask(
        attributionOutput: AsyncFuture<AttributionOutput?>?,
        forcedDecision: AttributionDecision?,
        advertiserIdFuture: AsyncFuture<AdvertiserIdInfo>,
        appSetIdFuture: AsyncFuture<AppSetIdInfo?>,
        trackingId: String?,
        trackingProvider: String,
        integritySecretFuture: AsyncFuture<String>,
    ) = object : Task<AttributionOutput?> {
        override suspend fun execute(): AttributionOutput? {
            var currentOutput: AsyncFuture<AttributionOutput?>? = attributionOutput

            var attributionTimestamps: AttributionTimestamps?
            var storedOutput: AttributionOutput? = null

            databaseInterface.openAttribution().use {
                attributionTimestamps = it.getAttributionTimestamps()
                it.setLastOpen(System.currentTimeMillis())

                try {
                    storedOutput = it.getStoredOutput(sdk.context)
                } catch (exception: Throwable) {
                    loggers.httpLogger.warn("Failed to parse stored response data", exception)
                }
            }

            val attributionDecision: AttributionDecision
            val attributionFuture: AsyncFuture<AttributionOutput?>?

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

                if (!attributionDecision.shouldFetchAttribution() && storedOutput != null) {
                    currentOutput = ValueFuture(storedOutput)
                    attributionFuture = null
                    latestApiAttributionOutput = null
                } else {
                    val referrerDetailsTask: AsyncFuture<ReferrerDetails?> = if (attributionDecision.shouldUseReferrerDetails(storedOutput != null)) {
                        val refDetails = referrerDetails
                        if (refDetails != null) {
                            refDetails
                        } else {
                            val temp = taskExecutor.executeAsFuture(
                                attributionParams.installReferrerProvider.newTask(sdk.context),
                            )
                            referrerDetails = temp
                            temp
                        }
                    } else {
                        ValueFuture(null)
                    }

                    var task: Task<AttributionOutput?> = AttributionTask(
                        attributionParams.intent,
                        databaseInterface,
                        AttributionTask.AttributionParams(
                            attributionParams.idManager,
                            advertiserIdFuture,
                            referrerDetailsTask,
                            appSetIdFuture,
                            trackingId,
                            trackingProvider,
                            attributionParams.claimProvider,
                            forcedDecision?.claimsTimeout ?: ClaimProvider.CLAIM_TIMEOUT_FAST_MS,
                            attributionParams.sdkConfig,
                            integritySecretFuture,
                        ),
                        sdk.httpClient,
                        loggers.httpLogger,
                        sdk,
                        versionBundle,
                    )
                    task = RetryingTask(
                        task,
                        deviceInfo,
                        loggers.httpLogger,
                        attributionParams.retryConfig.attributionRequestRetries,
                        AttributionErrorClassifier.getInstance(),
                        HttpClientImpl.GET_ATTRIBUTION_REQUEST_NAME,
                    )
                    currentOutput = taskExecutor.executeAsFuture(task)
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
                    sdk.callAttributionSubscriptions(storedResponse)
                } else {
                    loggers.httpLogger.debug("Using cached attribution, storedOutput is null")
                }
                return currentOutput?.await()
            }

            val storedResponse: AttributionResponse? = storedOutput?.getAttributionResponse()

            try {
                sdk.runAttributionTask(
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
        internal val reAttributionDecider: ReAttributionDecider,
        internal val installReferrerProvider: InstallReferrerProvider,
        internal val claimProvider: ClaimProvider,
        internal val retryConfig: RetryConfig,
        internal val reFetchReAttributionDelaySeconds: Long,
        internal val attributionRetryDelaySeconds: Long,
        internal val sdkConfig: JustTrackSdkConfig,
        internal val intent: Intent?,
    )
}
