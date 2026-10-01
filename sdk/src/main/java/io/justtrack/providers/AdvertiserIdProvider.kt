package io.justtrack.providers

import io.justtrack.AsyncFuture
import io.justtrack.attribution.AdvertiserIdInfo

/**
 * Class to encapsulate getting an {@link AdvertiserIdInfo} {@link Future}. Allows us to easily lock
 * this class without having to fear that we deadlock anything (we used to lock the {@link JustTrackSdkImpl}
 * for this - this lead to a problem when you needed to lock the SDK to process events, but another
 * thread had the SDK locked while blocking on publishing a new event as the blocking queue was full).
 */
internal fun interface AdvertiserIdProvider {
    fun provideAdvertiserId(): AsyncFuture<AdvertiserIdInfo>
}
