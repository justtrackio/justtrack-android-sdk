package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.log.Logger

internal class AnonymizeUserHandler internal constructor(
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val userIdFuture: AsyncFuture<String>,
    private val installInstanceIdFuture: AsyncFuture<String>,
    private val deviceIdFuture: AsyncFuture<AdvertiserIdInfo>,
) {
    @JvmName("anonymizeUser")
    internal fun anonymizeUser(): AsyncFuture<Boolean> {
        return taskExecutor.executeAsFuture(
            FixedRetryingTask(
                object : Task<Boolean> {
                    override suspend fun execute(): Boolean {
                        val advertiserId = deviceIdFuture.await().advertiserId
                        val body = DTOAnonymousUser(
                            installInstanceIdFuture.await(),
                            advertiserId,
                            deviceInfo.getAndroidIdOrDefault(""),
                        )

                        val result = httpClient.anonymizeUser(
                            logger,
                            advertiserId,
                            userIdFuture.await(),
                            installInstanceIdFuture.await(),
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
                HttpClientImpl.SEND_ANONYMIZE_REQUEST_NAME,
                FixedRetryingTask.DEFAULT_RETRY_DELAYS,
            ),
        )
    }
}
