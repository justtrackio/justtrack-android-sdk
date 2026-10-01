package io.justtrack

import android.content.Context
import io.justtrack.log.Logger
import io.justtrack.providers.AdvertiserIdProvider

internal class ReconnectHandler(
    private val context: Context,
    private val logger: Logger,
    private val attributionOutputProvider: AttributionOutputProvider,
    private val idManagers: IdManagers,
) {

    internal data class IdManagers(
        val customIdManager: CustomIdManager,
        val firebaseIdManager: FirebaseIdManager,
        val userIdProvider: UserIdProvider,
        val attributionIdManager: AttributionIdManager,
        val advertiserIdProvider: AdvertiserIdProvider,
    )

    fun onReconnect() {
        retryAttributionAfterReconnect()
        retrySendPersistId(PersistentIdStore.REASON_RECONNECT)
    }

    fun retryAttributionAfterReconnect() {
        var needToRefetch = false
        synchronized(this) {
            if (attributionOutputProvider.getOutput() is ErrorFuture<*>) {
                attributionOutputProvider.setOutput(null)
                attributionOutputProvider.setAttributionCanRetryAt(0L)
                needToRefetch = true
            }
        }

        if (needToRefetch) {
            logger.info("Fetching attribution again as it failed and we got a new network connection")
            attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION)
        }
    }

    fun retrySendPersistId(reason: String) {
        val pendingCustomUserId = CustomUserIdStore.getInstance().getPendingId(context)
        if (pendingCustomUserId != null) {
            idManagers.customIdManager.sendCustomUserId(
                pendingCustomUserId,
                idManagers.userIdProvider.provideUserIdFuture(),
                idManagers.attributionIdManager,
                idManagers.advertiserIdProvider,
                reason,
            )
        }

        val pendingFirebaseId = FirebaseIdStore.getInstance().getPendingId(context)
        if (pendingFirebaseId != null) {
            idManagers.firebaseIdManager.sendFirebaseId(
                idManagers.attributionIdManager,
                FirebaseIdManager.AttributionParams(
                    idManagers.userIdProvider.provideUserIdFuture(),
                    idManagers.advertiserIdProvider,
                    pendingFirebaseId,
                ),
                reason,
            )
        }
    }
}
