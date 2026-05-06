package io.justtrack

import android.content.Context
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder

internal class PublishFirebaseAppInstanceIdTask(
    private val context: Context,
    private val attributionIdManager: AttributionIdManager,
    private val loggerParams: LoggerParams,
    private val httpClient: HttpClient,
    private val attributionParams: AttributionParams,
    private val reason: String,
) : Task<Boolean> {
    override suspend fun execute(): Boolean {
        val userId = attributionParams.userIdFuture.await()
        val installId = attributionIdManager.getOrCreateInstallId().await()
        val thisTaskFuture = ResolvableFuture<Unit>()
        val existingRequest = RunningPublishRequests.offerFirebaseAppInstanceId(installId, attributionParams.firebaseId, thisTaskFuture)
        if (existingRequest != null) {
            existingRequest.await()

            return true
        }

        try {
            // ensure we didn't already finish publishing the user id at this point
            if (FirebaseIdStore.getInstance().getPendingId(context) == null) {
                return false
            }

            val advertiserIdValue = attributionParams.advertiserIdFuture.await().advertiserId
            val body: JSONEncodable = DTOPublishFirebaseAppInstanceIdRequest(userId, attributionParams.firebaseId)
            loggerParams.logger.info(
                "Publishing new Firebase app instance id",
                LoggerFieldsBuilder()
                    .with("firebaseAppInstanceId", attributionParams.firebaseId)
                    .with("reason", reason),
            )

            val result = httpClient.sendFirebaseAppInstanceId(
                loggerParams.logger,
                body,
                advertiserIdValue,
                userId,
                installId,
            )

            if (result.isSuccess) {
                FirebaseIdStore.getInstance()
                    .setStoredAtBackend(context, installId, attributionParams.firebaseId)
                thisTaskFuture.resolve(Unit)
            } else {
                thisTaskFuture.reject(result.exceptionOrNull() ?: Throwable("Failed to publish new Firebase app instance id with unknown error"))
            }

            return true
        } catch (exception: Throwable) {
            loggerParams.networkErrorLogger.logException(
                loggerParams.logger,
                exception,
                "Failed to publish new Firebase app instance id",
            )
            thisTaskFuture.reject(exception)

            throw exception
        }
    }

    internal data class AttributionParams(
        val userIdFuture: AsyncFuture<String>,
        val advertiserIdFuture: AsyncFuture<AdvertiserIdInfo>,
        val firebaseId: String,
    )

    internal data class LoggerParams(
        val logger: Logger,
        val networkErrorLogger: NetworkErrorLogger,
    )
}
