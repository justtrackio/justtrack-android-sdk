package io.justtrack

import android.content.Context
import io.justtrack.CustomUserIdStore.Companion.getInstance
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder

internal class CustomIdManager(
    private val context: Context,
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val networkErrorLogger: NetworkErrorLogger,
) {

    @JvmName("setCustomUserId")
    internal fun setCustomUserId(
        customId: String,
        userId: AsyncFuture<String>,
        attributionIdManager: AttributionIdManager,
        advertiserIdInfo: AsyncFuture<AdvertiserIdInfo>,
    ): AsyncFuture<Boolean> {
        if (!Validation.validUserId(customId)) {
            val exception = InvalidFieldException("customUserId", customId, 1, MAX_CUSTOM_ID_LENGTH, "ASCII")
            logger.warn("Not publishing invalid custom user id", exception)

            return ErrorFuture(exception)
        }
        return taskExecutor.executeAsFuture(
            setCustomUserIdTask(
                customId,
                userId,
                attributionIdManager,
                advertiserIdInfo,
            ),
        )
    }

    @JvmName("sendCustomUserId")
    internal fun sendCustomUserId(
        customUserId: String,
        userIdFuture: AsyncFuture<String>,
        attributionIdManager: AttributionIdManager,
        advertiserIdInfoFuture: AsyncFuture<AdvertiserIdInfo>,
        reason: String,
    ): AsyncFuture<Boolean> {
        return taskExecutor.executeAsFuture(
            FixedRetryingTask(
                PublishCustomUserIdTask(
                    context,
                    attributionIdManager,
                    logger,
                    networkErrorLogger,
                    httpClient,
                    customUserId,
                    reason,
                    userIdFuture,
                    advertiserIdInfoFuture,
                ),
                deviceInfo,
                logger,
                TrackingEventErrorClassifier.instance,
                HttpClientImpl.SEND_CUSTOM_USER_ID_REQUEST_NAME,
                FixedRetryingTask.DEFAULT_RETRY_DELAYS,
            ),
        )
    }

    private fun setCustomUserIdTask(
        customId: String,
        userIdFuture: AsyncFuture<String>,
        attributionIdManager: AttributionIdManager,
        advertiserIdInfoFuture: AsyncFuture<AdvertiserIdInfo>,
    ) = object : Task<Boolean> {
        override suspend fun execute(): Boolean {
            val installId = attributionIdManager.getOrCreateInstallId().await()

            if (!getInstance().storeNewId(context, installId, customId)) {
                // we already stored this, skip this
                val fields: LoggerFields = LoggerFieldsBuilder().with("customUserId", customId)
                logger.debug("Not publishing a custom user id twice", fields)

                return false
            }

            return sendCustomUserId(
                customId,
                userIdFuture,
                attributionIdManager,
                advertiserIdInfoFuture,
                PersistentIdStore.REASON_SEND,
            ).await()
        }
    }

    private companion object {
        private const val MAX_CUSTOM_ID_LENGTH = 4096
    }
}
