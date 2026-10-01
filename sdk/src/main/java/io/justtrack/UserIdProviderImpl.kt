package io.justtrack

import io.justtrack.executor.TaskExecutor
import io.justtrack.providers.AdvertiserIdProvider
import java.util.UUID
import java.util.concurrent.Future

/**
 * Class to encapsulate getting an [UUID] [Future]. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the [BaseJustTrackSdk]
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal class UserIdProviderImpl internal constructor(
    private val taskExecutor: TaskExecutor,
    private val deviceInfo: DeviceInfo,
    private val applicationPackageName: String,
    private val attributionParams: AttributionParams,
    private val logger: HttpLogger,
) : UserIdProvider {
    private var userIdInfo: AsyncFuture<String>? = null

    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    override fun provideUserIdFuture(): AsyncFuture<String> {
        val currentUserIdInfo = userIdInfo

        return if (currentUserIdInfo == null) {
            val result = taskExecutor.executeFuture(
                UserIdReaderTask(
                    deviceInfo,
                    logger,
                    attributionParams.attributionIdManager,
                    UserIdReaderTask.AttributionParams(attributionParams.advertiserIdProvider, attributionParams.trackingId),
                    applicationPackageName,
                ),
            )
            userIdInfo = result
            result
        } else {
            currentUserIdInfo
        }
    }

    internal data class AttributionParams(
        val attributionIdManager: AttributionIdManager,
        val advertiserIdProvider: AdvertiserIdProvider,
        val trackingId: String?,
    )
}
