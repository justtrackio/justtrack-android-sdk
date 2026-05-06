package io.justtrack

import android.content.Context
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder

internal class PublishCustomUserIdTask(
    private val context: Context,
    private val attributionIdManager: AttributionIdManager,
    private val logger: Logger,
    private val networkErrorLogger: NetworkErrorLogger,
    private val httpClient: HttpClient,
    private val customUserId: String,
    private val reason: String,
    private val userIdFuture: AsyncFuture<String>,
    private val advertiserIdFuture: AsyncFuture<AdvertiserIdInfo>,
) : Task<Boolean> {
    override suspend fun execute(): Boolean {
        val fields: LoggerFields =
            LoggerFieldsBuilder()
                .with("customUserId", customUserId)
                .with("reason", reason)

        val userId = userIdFuture.await()
        val installId = attributionIdManager.getOrCreateInstallId().await()
        val thisTaskFuture = ResolvableFuture<Unit>()
        val existingRequest =
            RunningPublishRequests.offerCustomUserId(installId, customUserId, thisTaskFuture)
        if (existingRequest != null) {
            existingRequest.await()

            return true
        }

        try {
            // ensure we didn't already finish publishing the user id at this
            if (CustomUserIdStore.getInstance().getPendingId(context) == null) {
                return true
            }

            val advertiserIdValue = advertiserIdFuture.await().advertiserId
            val body = DTOPublishCustomUserIdRequest(installId, customUserId)
            logger.info("Publishing new custom user id", fields)

            val result = httpClient.sendCustomUserId(
                logger,
                body,
                advertiserIdValue,
                userId,
                installId,
            )

            if (result.isSuccess) {
                CustomUserIdStore.getInstance()
                    .setStoredAtBackend(context, installId, customUserId)
                thisTaskFuture.resolve(Unit)
            } else {
                thisTaskFuture.reject(result.exceptionOrNull() ?: Throwable("Failed to publish custom user id with unknown error"))
            }

            return true
        } catch (exception: Throwable) {
            networkErrorLogger.logException(
                logger,
                exception,
                "Failed to publish custom user id ${exception.message}",
            )
            thisTaskFuture.reject(exception)

            throw exception
        }
    }
}
