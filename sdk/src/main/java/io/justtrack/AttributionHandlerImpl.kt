package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.attribution.AttributionHandler
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.retargeting.RetargetingParameters
import io.justtrack.retargeting.RetargetingParametersListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.RejectedExecutionException
import kotlin.math.max

internal class AttributionHandlerImpl(
    private val taskExecutor: TaskExecutor,
    private val attributionParams: AttributionParams,
    private val loggers: Loggers,
    private val subscribers: Subscribers,
    private val preliminaryRetargetingParametersImpl: PreliminaryRetargetingParametersImpl?,
) : AttributionHandler {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun handleAttribution(
        attributionFuture: AsyncFuture<AttributionOutput>,
        storedResponse: AttributionResponse?,
        attributionTimestamps: AttributionTimestamps?,
        forcedDecision: AttributionDecision?,
        attributionDecision: AttributionDecision,
    ) {
        try {
            val output = attributionFuture.get()
            onAttributionDone(output)
            if (attributionTimestamps == null) {
                loggers.httpLogger.debug("Fetched first attribution")
            } else if (!attributionDecision.shouldFetchAttribution) {
                loggers.httpLogger.info("Attribution was not needed, but was not cached")
            } else {
                val attributionAge = attributionTimestamps.getLastAttributionAt() - attributionTimestamps.getFirstAttributionAt()
                val now = System.currentTimeMillis()
                val sinceLastOpen = now - attributionTimestamps.getLastOpenAt()
                val sinceLastAttribution = now - attributionTimestamps.getLastAttributionAt()
                val fields: LoggerFields = LoggerFieldsBuilder()
                    .with("attributionAge", attributionAge)
                    .with("sinceLastOpen", sinceLastOpen)
                    .with("sinceLastAttribution", sinceLastAttribution)
                    .with("force", forcedDecision != null)
                loggers.httpLogger.debug("Attribution was fetched again because re-attribution was needed", fields)
            }

            subscribers.attributionSubscriptionHandler.callAttributionSubscriptions(output.getAttributionResponse())
            callRetargetingParametersSubscriptions(output.getRetargetingParameters())

            val needsClaimsRefetch = output.getAttributionResponse().getCampaign().isOrganic &&
                output.didClaimsTimeOut() &&
                attributionDecision.isFastClaimsTimeout
            val needsReAttributionRefetch = attributionDecision.isFetchRetargetingAttribution &&
                (storedResponse == null || output.getAttributionResponse().getInstallId() == storedResponse.getInstallId()) &&
                attributionParams.attributionOutputProvider.getReFetchReAttributionDelaySeconds() > 0

            // check if we expected a re-attribution and did not get one
            if (needsReAttributionRefetch) {
                val fetchWithNeedClaimRefetch = (
                    "Fetching attribution again (with longer claims timeout) " +
                        "because the retargeting delay expired and previously the attribution did not change"
                    )

                val fetchWithoutNeedClaimRefetch = (
                    "Fetching attribution again because the retargeting delay " +
                        "expired and previously the attribution did not change"
                    )
                fetchAttributionAgainAfter(
                    if (needsClaimsRefetch) fetchWithNeedClaimRefetch else fetchWithoutNeedClaimRefetch,
                    if (needsClaimsRefetch) {
                        AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED.withSlowClaimTimeout()
                    } else {
                        AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED
                    },
                    attributionParams.attributionOutputProvider.getReFetchReAttributionDelaySeconds(),
                )
            } else {
                val localPreliminaryRetargetingParametersImpl: PreliminaryRetargetingParametersImpl?
                // Deadlock-Safety: We only read a field and store it in a local variable.
                synchronized(this) {
                    localPreliminaryRetargetingParametersImpl = preliminaryRetargetingParametersImpl
                }
                localPreliminaryRetargetingParametersImpl?.resolve(output)
                // trigger fetching the attribution again (with the claims this time, if possible)
                if (needsClaimsRefetch) {
                    fetchAttributionAgainAfter(
                        "Fetching attribution again with longer timeout while waiting for claims",
                        AttributionDecision.FETCH_FIRST_ATTRIBUTION.withSlowClaimTimeout(),
                        DEFAULT_CLAIM_RE_FETCH_DELAY, // we don't really need to wait for a long time before trying again
                    )
                }
            }
        } catch (exception: Exception) {
            handleException(exception, attributionFuture)
        }
    }

    @VisibleForTesting
    internal fun handleException(exception: Exception, attributionFuture: AsyncFuture<AttributionOutput>) {
        loggers.networkErrorLogger.logException(
            loggers.httpLogger,
            exception,
            "Failed to wait for attribution",
        )

        // Deadlock-Safety: We only update local state, classify errors, or call simple functions
        synchronized(this) {
            // if we still have the same value as before, replace it by an error future so we know we can retry getting the attribution
            if (attributionParams.attributionOutputProvider.getLatestApiAttributionOutput() === attributionFuture) {
                attributionParams.attributionOutputProvider.setOutput(ErrorFuture(exception))
                if (AttributionErrorClassifier.getInstance().unrecoverable(exception)) {
                    // use an hour as "infinity" - if the app is still running after 1h, we can take the hit, if not, we don't leave the chance
                    // that something lingers in memory and the next time we run we can't handle this
                    attributionParams.attributionOutputProvider.setAttributionCanRetryAt(System.currentTimeMillis() + ONE_HOUR)
                } else {
                    // retry only after at least the wait time is over
                    val waitTime = AttributionErrorClassifier.getInstance().waitTime(exception).toLong()
                    attributionParams.attributionOutputProvider.setAttributionCanRetryAt(
                        (
                            System.currentTimeMillis() + max(
                                (attributionParams.attributionOutputProvider.getAttributionRetryDelaySeconds() * MS_TO_S).toDouble(),
                                waitTime.toDouble(),
                            )
                            ).toLong(),
                    )
                }
            }
        }
    }

    @VisibleForTesting
    internal fun fetchAttributionAgainAfter(message: String, forcedDecision: AttributionDecision, delaySeconds: Long) {
        coroutineScope.launch {
            delay(delaySeconds * MS_TO_S)
            loggers.httpLogger.info(message)
            attributionParams.attributionOutputProvider.provideAttributionOutput(forcedDecision)
        }
    }

    @VisibleForTesting
    internal fun callRetargetingParametersSubscriptions(retargetingParameters: RetargetingParameters?) {
        if (retargetingParameters == null) {
            return
        }
        subscribers.retargetingParametersSubscriptions.call { listener ->
            taskExecutor.execute(
                { listener.onRetargetingParametersReceived(retargetingParameters) },
                { exception: RejectedExecutionException ->
                    loggers.networkErrorLogger.logException(
                        loggers.httpLogger,
                        exception,
                        "Could not call retargeting parameter subscription, SDK is shutting down",
                    )
                },
                false,
            )
        }
    }

    @VisibleForTesting
    internal fun onAttributionDone(output: AttributionOutput) {
        val installId = output.getAttributionResponse().getInstallId()
        attributionParams.attributionIdManager.checkInstallIdChange(
            installId,
            attributionParams.userIdProvider.provideUserIdFuture(),
            attributionParams.advertiserIdProvider,
        )
    }

    internal data class Loggers(
        internal val httpLogger: HttpLogger,
        internal val networkErrorLogger: NetworkErrorLogger,
    )

    internal data class Subscribers(
        internal val attributionSubscriptionHandler: AttributionSubscriptionHandler,
        internal val retargetingParametersSubscriptions: SubscriptionManager<RetargetingParametersListener>,
    )

    internal data class AttributionParams(
        internal val attributionOutputProvider: AttributionOutputProvider,
        internal val attributionIdManager: AttributionIdManager,
        internal val userIdProvider: UserIdProvider,
        internal val advertiserIdProvider: AdvertiserIdProvider,
    )

    companion object {
        private const val MS_TO_S = 1000L
        private const val ONE_HOUR = 3600 * MS_TO_S
        private const val DEFAULT_CLAIM_RE_FETCH_DELAY = 3L
    }
}
