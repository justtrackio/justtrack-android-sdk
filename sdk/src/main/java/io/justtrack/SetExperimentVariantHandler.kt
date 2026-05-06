package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.log.Logger
import java.util.Date

internal class SetExperimentVariantHandler internal constructor(
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val httpClient: HttpClient,
    private val logger: Logger,
) {
    @JvmName("setExperimentVariant")
    internal fun setExperimentVariant(
        attributionParams: AttributionParams,
        experimentParams: ExperimentParams,
        sdkVersion: Version,
        appVersion: ApplicationVersion,
        happenedAt: Date?,
    ): AsyncFuture<Void?> {
        return taskExecutor.executeAsFuture(
            FixedRetryingTask(
                {
                    val body = DTOSetExperimentVariant(
                        attributionParams.installInstanceIdFuture.await(),
                        sdkVersion.name,
                        appVersion.getVersionName(),
                        appVersion.getVersionCode(),
                        deviceInfo.osVersion,
                        experimentParams.experiment,
                        experimentParams.variant,
                        experimentParams.tags,
                        happenedAt,
                    )

                    val result = httpClient.setExperimentVariant(
                        logger,
                        body,
                        attributionParams.deviceIdFuture.await().advertiserId,
                        attributionParams.userIdFuture.await(),
                        attributionParams.installInstanceIdFuture.await(),
                    )

                    if (result.isSuccess) {
                        null
                    } else {
                        throw result.exceptionOrNull()
                            ?: IllegalStateException("SetExperimentVariant failed with unknown exception")
                    }
                },
                deviceInfo,
                logger,
                TrackingEventErrorClassifier.instance,
                HttpClientImpl.SET_EXPERIMENT_VARIANT_REQUEST_NAME,
                RETRY_DELAYS,
            ),
        )
    }

    internal data class AttributionParams(
        val installInstanceIdFuture: AsyncFuture<String>,
        val userIdFuture: AsyncFuture<String>,
        val deviceIdFuture: AsyncFuture<AdvertiserIdInfo>,
    )

    internal data class ExperimentParams(
        val experiment: String,
        val variant: String,
        val tags: List<String>?,
    )

    private companion object {
        private val RETRY_DELAYS = listOf(10, 10, 10)
    }
}
