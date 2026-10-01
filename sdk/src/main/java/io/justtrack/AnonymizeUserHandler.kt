package io.justtrack

import io.justtrack.api.PrivacyApi
import io.justtrack.api.PrivacyApiImpl
import io.justtrack.dtos.DTOAnonymousUser
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.Logger
import io.justtrack.providers.AdvertiserIdProvider

internal class AnonymizeUserHandler internal constructor(
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val privacyApi: PrivacyApi,
    private val logger: Logger,
    private val attr: AttributionParams,
    private val retryDelays: List<Int> = FixedRetryingTask.DEFAULT_RETRY_DELAYS,
) {

    internal constructor(
        taskExecutor: TaskExecutor,
        deviceInfo: DeviceInfo,
        privacyApi: PrivacyApi,
        logger: Logger,
        attr: AttributionParams,
    ) : this(
        taskExecutor,
        deviceInfo,
        privacyApi,
        logger,
        attr,
        FixedRetryingTask.DEFAULT_RETRY_DELAYS,
    )

    @JvmName("anonymizeUser")
    internal fun anonymizeUser(): AsyncFuture<Boolean> {
        return taskExecutor.executeFuture(
            FixedRetryingTask(
                object : Task<Boolean> {
                    override suspend fun execute(): Boolean {
                        val advertiserId = attr.advertiserIdProvider.provideAdvertiserId().await().advertiserId
                        val body = DTOAnonymousUser(
                            attr.installInstanceIdFuture.await(),
                            advertiserId,
                            deviceInfo.getAndroidIdOrDefault(""),
                        )

                        val result = privacyApi.anonymizeUser(
                            advertiserId,
                            attr.userIdFuture.await(),
                            attr.installInstanceIdFuture.await(),
                            body,
                        )

                        if (result.isSuccess) {
                            return result.isSuccess
                        } else {
                            throw result.exceptionOrNull()
                                ?: IllegalStateException("AnonymizeUser failed with unknown exception")
                        }
                    }
                },
                deviceInfo,
                logger,
                TrackingEventErrorClassifier.instance,
                PrivacyApiImpl.SEND_ANONYMIZE_REQUEST_NAME,
                retryDelays,
            ),
        )
    }

    internal data class AttributionParams(
        internal val userIdFuture: AsyncFuture<String>,
        internal val installInstanceIdFuture: AsyncFuture<String>,
        internal val advertiserIdProvider: AdvertiserIdProvider,
    )
}
