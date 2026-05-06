package io.justtrack

import io.justtrack.attribution.AdvertiserIdInfo
import java.util.UUID
import java.util.concurrent.Future

/**
 * Class to encapsulate getting an [UUID] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class UserIdProvider internal constructor(
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val applicationPackageName: String,
) {
    private var userIdInfo: AsyncFuture<UUID>? = null

    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    @JvmName("provideUserIdFuture")
    internal fun provideUserIdFuture(
        attributionIdManager: AttributionIdManager,
        logger: HttpLogger,
        advertiserIdFuture: AsyncFuture<AdvertiserIdInfo>,
        trackingId: String?,
    ): AsyncFuture<UUID> {
        val currentUserIdInfo = userIdInfo

        return if (currentUserIdInfo == null) {
            val result = taskExecutor.executeAsFuture(
                UserIdReaderTask(
                    deviceInfo,
                    logger,
                    attributionIdManager,
                    UserIdReaderTask.AttributionParams(advertiserIdFuture, trackingId),
                    applicationPackageName,
                ),
            )
            userIdInfo = result
            result
        } else {
            currentUserIdInfo
        }
    }
}
