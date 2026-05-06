package io.justtrack

import android.content.Context
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder

internal class FirebaseIdManager(
    private val context: Context,
    private val taskExecutor: TaskExecutor,
    private val httpClient: HttpClient,
    private val deviceInfo: DeviceInfo,
    private val logger: Logger,
    private val networkErrorLogger: NetworkErrorLogger,
) {
    @JvmName("setFirebaseId")
    internal fun setFirebaseId(attributionIdManager: AttributionIdManager, attributionParams: AttributionParams): AsyncFuture<Boolean> {
        if (!Validation.validFirebaseAppInstanceId(attributionParams.firebaseAppInstanceId)) {
            val exception = InvalidFieldException(
                "firebaseAppInstanceId",
                attributionParams.firebaseAppInstanceId,
                MIN_FIREBASE_ID_LENGTH,
                MAX_FIREBASE_ID_LENGTH,
                "ASCII",
            )
            logger.warn("Not publishing invalid Firebase app instance id", exception)

            return ErrorFuture(exception)
        }

        return taskExecutor.executeAsFuture(
            setFirebaseTask(
                attributionIdManager,
                attributionParams,
            ),
        )
    }

    @JvmName("sendFirebaseId")
    internal fun sendFirebaseId(
        attributionIdManager: AttributionIdManager,
        attributionParams: AttributionParams,
        reason: String,
    ): AsyncFuture<Boolean> {
        logger.info("performFirebaseIdSend with $reason")

        val task = PublishFirebaseAppInstanceIdTask(
            context,
            attributionIdManager,
            PublishFirebaseAppInstanceIdTask.LoggerParams(logger, networkErrorLogger),
            httpClient,
            PublishFirebaseAppInstanceIdTask.AttributionParams(
                attributionParams.userId,
                attributionParams.advertiserIdInfo,
                attributionParams.firebaseAppInstanceId,
            ),
            reason,
        )

        return taskExecutor.executeAsFuture(
            FixedRetryingTask(
                task,
                deviceInfo,
                logger,
                TrackingEventErrorClassifier.instance,
                HttpClientImpl.SEND_FIREBASE_APP_INSTANCE_ID_REQUEST_NAME,
                FixedRetryingTask.DEFAULT_RETRY_DELAYS,
            ),
        )
    }

    private fun setFirebaseTask(attributionIdManager: AttributionIdManager, attributionParams: AttributionParams) = object : Task<Boolean> {
        override suspend fun execute(): Boolean {
            val installId = attributionIdManager.getOrCreateInstallId().await()
            if (!FirebaseIdStore.getInstance().storeNewId(context, installId, attributionParams.firebaseAppInstanceId)) {
                // we already stored this, skip this
                val fields: LoggerFields = LoggerFieldsBuilder().with("firebaseId", attributionParams.firebaseAppInstanceId)
                logger.debug("Not publishing the same firebaseId twice", fields)

                return false
            }

            return sendFirebaseId(
                attributionIdManager,
                attributionParams,
                PersistentIdStore.REASON_SEND,
            ).await()
        }
    }

    internal data class AttributionParams(
        val userId: AsyncFuture<String>,
        val advertiserIdInfo: AsyncFuture<AdvertiserIdInfo>,
        val firebaseAppInstanceId: String,
    )

    private companion object {
        private const val MIN_FIREBASE_ID_LENGTH = 8
        private const val MAX_FIREBASE_ID_LENGTH = 256
    }
}
