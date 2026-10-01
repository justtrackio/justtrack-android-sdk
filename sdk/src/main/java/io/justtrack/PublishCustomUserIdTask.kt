package io.justtrack

import android.content.Context
import io.justtrack.api.AttributionApi
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.providers.AdvertiserIdProvider

internal class PublishCustomUserIdTask(
    private val context: Context,
    private val attributionIdManager: AttributionIdManager,
    private val logger: Logger,
    private val networkErrorLogger: NetworkErrorLogger,
    private val attributionApi: AttributionApi,
    private val reason: String,
    private val attrParams: AttributionParams,
) : Task<Boolean> {
    override suspend fun execute(): Boolean {
        val fields: LoggerFields =
            LoggerFieldsBuilder()
                .with("customUserId", attrParams.customUserId)
                .with("reason", reason)

        val userId = attrParams.userIdFuture.await()
        val installId = attributionIdManager.getOrCreateInstallId().await()
        val thisTaskFuture = ResolvableFuture<Unit>()
        val existingRequest =
            RunningPublishRequests.offerCustomUserId(installId, attrParams.customUserId, thisTaskFuture)
        if (existingRequest != null) {
            existingRequest.await()

            return true
        }

        try {
            // ensure we didn't already finish publishing the user id at this
            if (CustomUserIdStore.getInstance().getPendingId(context) == null) {
                return true
            }

            val advertiserIdValue = attrParams.advertiserIdProvider.provideAdvertiserId().await().advertiserId
            val body = DTOPublishCustomUserIdRequest(installId, attrParams.customUserId)
            logger.info("Publishing new custom user id", fields)

            val result = attributionApi.sendCustomUserId(
                body,
                advertiserIdValue,
                userId,
                installId,
            )

            if (result.isSuccess) {
                CustomUserIdStore.getInstance()
                    .setStoredAtBackend(context, installId, attrParams.customUserId)
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

    internal data class AttributionParams(
        internal val customUserId: String,
        internal val userIdFuture: AsyncFuture<String>,
        internal val advertiserIdProvider: AdvertiserIdProvider,
    )
}
