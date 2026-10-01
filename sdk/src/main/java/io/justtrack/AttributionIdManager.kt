package io.justtrack

import android.content.Context
import io.justtrack.executor.TaskExecutor
import io.justtrack.providers.AdvertiserIdProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal class AttributionIdManager internal constructor(
    private val taskExecutor: TaskExecutor,
    private val context: Context,
    private val databaseInterface: DatabaseInterface,
    private val customIdManager: CustomIdManager,
    private val firebaseIdManager: FirebaseIdManager,
) {
    @Volatile
    private var cachedInstallId: String? = null
    private val getOrCreateInstallIdMutex = Mutex()

    @JvmName("getOrCreateInstallId")
    internal fun getOrCreateInstallId(): AsyncFuture<String> {
        return taskExecutor.executeFuture(getOrCreateStoredInstallId())
    }

    @Synchronized
    @JvmName("getUserId")
    fun getStoredUserId(): AsyncFuture<UUID?> = taskExecutor.executeFuture(StoredUserIdGetterTask(databaseInterface))

    @JvmName("checkInstallIdChange")
    fun checkInstallIdChange(newInstallId: String, userIdFuture: AsyncFuture<String>, advertiserIdProvider: AdvertiserIdProvider) {
        CoroutineScope(Dispatchers.IO).launch {
            if (newInstallId != cachedInstallId) {
                cachedInstallId = newInstallId
                onInstallIdChange(newInstallId, userIdFuture, advertiserIdProvider)
            }
        }
    }

    private fun getOrCreateStoredInstallId() = object : Task<String> {
        // Thread-Safety: The task is only query and storing to DB, should be quite fast.
        @Throws(RuntimeException::class)
        override suspend fun execute(): String {
            return getOrCreateInstallIdMutex.withLock {
                val existingInstallInstanceId = databaseInterface.openAttribution().use {
                    it.getInstallId()
                }

                cachedInstallId = existingInstallInstanceId

                if (!existingInstallInstanceId.isNullOrEmpty()) {
                    return@withLock existingInstallInstanceId
                } else {
                    val uuid = UUID.randomUUID().toString()

                    cachedInstallId = uuid

                    storeInstallId(uuid)
                    return@withLock uuid
                }
            }
        }
    }

    internal fun onInstallIdChange(newInstallId: String, userIdFuture: AsyncFuture<String>, advertiserIdProvider: AdvertiserIdProvider) {
        val customUserId = CustomUserIdStore.getInstance().getPendingWithNewInstallId(context, newInstallId)
        val firebaseId = FirebaseIdStore.getInstance().getPendingWithNewInstallId(context, newInstallId)
        if (customUserId != null) {
            customIdManager.sendCustomUserId(
                customUserId,
                userIdFuture,
                this,
                advertiserIdProvider,
                PersistentIdStore.REASON_INSTALL_ID_CHANGED,
            )
        }

        if (firebaseId != null) {
            firebaseIdManager.sendFirebaseId(
                this,
                FirebaseIdManager.AttributionParams(
                    userIdFuture,
                    advertiserIdProvider,
                    firebaseId,
                ),
                PersistentIdStore.REASON_INSTALL_ID_CHANGED,
            )
        }
    }

    internal class StoredUserIdGetterTask(private val databaseInterface: DatabaseInterface) : Task<UUID?> {
        @Throws(RuntimeException::class)
        override suspend fun execute(): UUID? {
            val userIdString: String? = databaseInterface.openAttribution().use {
                it.getUserId()
            }
            if (userIdString.isNullOrEmpty()) return null
            return UUID.fromString(userIdString)
        }
    }

    private suspend fun storeInstallId(installId: String) {
        databaseInterface.openAttribution().use { it.setInstallId(installId) }
    }

    internal suspend fun storeUserId(userId: String) {
        databaseInterface.openAttribution().use { it.setUserId(userId) }
    }
}
