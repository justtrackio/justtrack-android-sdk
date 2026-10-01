package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.executor.TaskExecutor
import io.justtrack.sdk.integrity.Integrity
import java.util.UUID
import java.util.concurrent.Future

/**
 * Class to encapsulate getting an [UUID] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class IntegritySecretProvider internal constructor(
    private val taskExecutor: TaskExecutor,
    private val databaseInterface: DatabaseInterface,
) {

    private val integrity = Integrity()

    private var integritySecret: AsyncFuture<String>? = null

    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    @JvmName("provideIntegritySecret")
    internal fun provideIntegritySecret(installInstanceIdFuture: AsyncFuture<String>): AsyncFuture<String> {
        val currentIntegritySecret = integritySecret

        return if (currentIntegritySecret == null) {
            val result = taskExecutor.executeFuture(getIntegritySecret(installInstanceIdFuture))
            integritySecret = result
            result
        } else {
            currentIntegritySecret
        }
    }

    @VisibleForTesting
    internal fun getIntegritySecret(installInstanceIdFuture: AsyncFuture<String>): Task<String> = object : Task<String> {
        @Throws(RuntimeException::class)
        override suspend fun execute(): String {
            var cachedIntegritySecret: String?

            databaseInterface.openAttribution().use {
                cachedIntegritySecret = it.getIntegritySecret()
            }
            return cachedIntegritySecret?.let {
                return@let it
            } ?: run {
                val installInstanceId = installInstanceIdFuture.await()
                val integritySecretKey = integritySecretKeyFirst + integritySecretKeySecond
                val newSecret = integrity.generateSecret(installInstanceId, integritySecretKey)
                databaseInterface.openAttribution().use {
                    it.setIntegritySecret(newSecret)
                }
                return@run newSecret
            }
        }
    }
}
