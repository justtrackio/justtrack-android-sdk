package io.justtrack

import android.content.Context
import io.justtrack.CustomUserIdStore.Companion.getInstance
import io.justtrack.api.AttributionApi
import io.justtrack.api.AttributionApiImpl
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.providers.AdvertiserIdProvider

internal class CustomIdManager(
    private val context: Context,
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val attributionApi: AttributionApi,
    private val logger: Logger,
    private val networkErrorLogger: NetworkErrorLogger,
) {

    @JvmName("setCustomUserId")
    internal fun setCustomUserId(
        customId: String,
        userId: AsyncFuture<String>,
        attributionIdManager: AttributionIdManager,
        advertiserIdProvider: AdvertiserIdProvider,
    ): AsyncFuture<Boolean> {
        if (!Validation.validUserId(customId)) {
            val exception = InvalidFieldException("customUserId", customId, 1, MAX_CUSTOM_ID_LENGTH, "ASCII")
            logger.warn("Not publishing invalid custom user id", exception)

            return ErrorFuture(exception)
        }
        return taskExecutor.executeFuture(
            setCustomUserIdTask(
                customId,
                userId,
                attributionIdManager,
                advertiserIdProvider,
            ),
        )
    }

    @JvmName("sendCustomUserId")
    internal fun sendCustomUserId(
        customUserId: String,
        userIdFuture: AsyncFuture<String>,
        attributionIdManager: AttributionIdManager,
        advertiserIdProvider: AdvertiserIdProvider,
        reason: String,
    ): AsyncFuture<Boolean> {
        return taskExecutor.executeFuture(
            FixedRetryingTask(
                PublishCustomUserIdTask(
                    context,
                    attributionIdManager,
                    logger,
                    networkErrorLogger,
                    attributionApi,
                    reason,
                    PublishCustomUserIdTask.AttributionParams(customUserId, userIdFuture, advertiserIdProvider),
                ),
                deviceInfo,
                logger,
                TrackingEventErrorClassifier.instance,
                AttributionApiImpl.SEND_CUSTOM_USER_ID_REQUEST_NAME,
                FixedRetryingTask.DEFAULT_RETRY_DELAYS,
            ),
        )
    }

    private fun setCustomUserIdTask(
        customId: String,
        userIdFuture: AsyncFuture<String>,
        attributionIdManager: AttributionIdManager,
        advertiserIdProvider: AdvertiserIdProvider,
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
                advertiserIdProvider,
                PersistentIdStore.REASON_SEND,
            ).await()
        }
    }

    private companion object {
        private const val MAX_CUSTOM_ID_LENGTH = 4096
    }
}
