package io.justtrack

import io.justtrack.api.ExperimentApi
import io.justtrack.api.ExperimentApiImpl
import io.justtrack.dtos.DTOSetExperimentVariant
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.Logger
import io.justtrack.providers.AdvertiserIdProvider
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

internal class ExperimentVariantHandler internal constructor(
    private val deps: Dependencies,
    private val attributionParams: AttributionParams,
) {

    internal data class Dependencies(
        val taskExecutor: TaskExecutor,
        val deviceInfo: DeviceInfo,
        val experimentApi: ExperimentApi,
        val logger: Logger,
        val isTracking: AtomicBoolean,
        val appVersionProvider: AppVersionProvider,
        val sdkVersion: Version,
    )

    internal data class AttributionParams(
        val installInstanceIdFuture: () -> AsyncFuture<String>,
        val userIdFuture: () -> AsyncFuture<String>,
        val advertiserIdProvider: AdvertiserIdProvider,
    )

    @JvmName("setExperimentVariant")
    fun setExperimentVariant(experiment: String, variant: String, tags: List<String?>, happenedAt: Date?): AsyncFuture<Void?> {
        if (!deps.isTracking.get()) return ErrorFuture(SdkNotTrackingException())

        return try {
            val safeExperiment = validateExperiment(experiment)
            val safeVariant = validateVariant(variant)
            val safeTags = validateTags(tags)
            executeRequest(safeExperiment, safeVariant, safeTags, happenedAt)
        } catch (exception: InvalidFieldException) {
            deps.logger.warn("Unable to set experiment variant with invalid input", exception)
            ErrorFuture(exception)
        }
    }

    private fun validateExperiment(experiment: String): String {
        if (!Validation.validCommonInput(experiment, MAX_EXPERIMENT_LENGTH)) {
            throw InvalidFieldException("experiment", experiment, MAX_EXPERIMENT_LENGTH, "ASCII")
        }
        return experiment
    }

    private fun validateVariant(variant: String): String {
        if (!Validation.validCommonInput(variant, MAX_VARIANT_LENGTH)) {
            throw InvalidFieldException("variant", variant, MAX_VARIANT_LENGTH, "ASCII")
        }
        return variant
    }

    private fun validateTags(tags: List<String?>): List<String> {
        if (tags.size > MAX_TAG_COUNT) {
            throw InvalidFieldException("tags", MAX_TAG_COUNT, tags.size)
        }

        val invalidIndex = tags.indexOfFirst { tag ->
            tag == null || !Validation.validCommonInput(tag, MAX_TAG_LENGTH)
        }
        if (invalidIndex >= 0) {
            throw InvalidFieldException("tags", tags[invalidIndex] ?: "", MAX_TAG_LENGTH, "ASCII")
        }

        return tags.filterNotNull()
    }

    private fun executeRequest(experiment: String, variant: String, tags: List<String>?, happenedAt: Date?): AsyncFuture<Void?> {
        return deps.taskExecutor.executeFuture(
            FixedRetryingTask(
                {
                    val body = DTOSetExperimentVariant(
                        attributionParams.installInstanceIdFuture().await(),
                        deps.sdkVersion.name,
                        deps.appVersionProvider.currentVersion.getVersionName(),
                        deps.appVersionProvider.currentVersion.getVersionCode(),
                        deps.deviceInfo.osVersion,
                        experiment,
                        variant,
                        tags,
                        happenedAt,
                    )

                    val result = deps.experimentApi.setExperimentVariant(
                        body,
                        attributionParams.advertiserIdProvider.provideAdvertiserId().await().advertiserId,
                        attributionParams.userIdFuture().await(),
                        attributionParams.installInstanceIdFuture().await(),
                    )

                    if (result.isSuccess) {
                        null
                    } else {
                        throw result.exceptionOrNull()
                            ?: IllegalStateException("SetExperimentVariant failed with unknown exception")
                    }
                },
                deps.deviceInfo,
                deps.logger,
                TrackingEventErrorClassifier.instance,
                ExperimentApiImpl.SET_EXPERIMENT_VARIANT_REQUEST_NAME,
                RETRY_DELAYS,
            ),
        )
    }

    private companion object {
        private const val MAX_EXPERIMENT_LENGTH = 256
        private const val MAX_VARIANT_LENGTH = 256
        private const val MAX_TAG_LENGTH = 64
        private const val MAX_TAG_COUNT = 5
        private val RETRY_DELAYS = listOf(10, 10, 10)
    }
}
