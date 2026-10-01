package io.justtrack.ads

import io.justtrack.attribution.AdvertiserIdInfo

/**
 * Read advertiser Id from device.
 */
internal fun interface DeviceAdvertiserIdReader {
    fun readAdvertiserId(): AdvertiserIdInfo
}
