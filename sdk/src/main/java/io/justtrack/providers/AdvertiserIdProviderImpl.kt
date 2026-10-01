package io.justtrack.providers

import android.content.Context
import io.justtrack.AsyncFuture
import io.justtrack.HttpLogger
import io.justtrack.ads.AdvertiserIdProcessingTask
import io.justtrack.ads.DeviceAdvertiserIdReaderImpl
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.executor.TaskExecutor

/**
 * Read advertiser Id from device.
 */
internal class AdvertiserIdProviderImpl(
    private val context: Context,
    private val taskExecutor: TaskExecutor,
    private val logger: HttpLogger,
) : AdvertiserIdProvider {
    private var advertiserIdInfo: AsyncFuture<AdvertiserIdInfo>? = null

    // Deadlock-Safety: executeAsFuture is not locking anything (besides the executor maybe).
    @Synchronized
    override fun provideAdvertiserId(): AsyncFuture<AdvertiserIdInfo> {
        var localAdvertiserIdInfo = advertiserIdInfo
        if (localAdvertiserIdInfo == null) {
            localAdvertiserIdInfo = taskExecutor.executeFuture(
                AdvertiserIdProcessingTask(DeviceAdvertiserIdReaderImpl(context), logger),
            )
            advertiserIdInfo = localAdvertiserIdInfo
            return localAdvertiserIdInfo
        } else {
            return localAdvertiserIdInfo
        }
    }
}
