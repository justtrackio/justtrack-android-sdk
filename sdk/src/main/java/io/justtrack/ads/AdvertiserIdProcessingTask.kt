package io.justtrack.ads

import io.justtrack.HttpLogger
import io.justtrack.Task
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.attribution.AdvertiserIdInfoImpl
import java.util.UUID

internal class AdvertiserIdProcessingTask internal constructor(
    private val deviceAdvertiserIdReader: DeviceAdvertiserIdReader,
    private val logger: HttpLogger,
) : Task<AdvertiserIdInfo> {

    override suspend fun execute(): AdvertiserIdInfo {
        val advertiserIdInfo =
            try {
                val info = deviceAdvertiserIdReader.readAdvertiserId()
                var advertiserId = info.advertiserId
                var limitedAdTracking = info.isLimitedAdTracking
                // two things are checked in the next line:
                //   - is the advertiser ID the zero UUID? Android reports limited ad tracking as that UUID in some cases
                //   - is the advertiser ID in a valid format? One would think UUID.fromString would ensure
                //     that this is the case, but there are some devices out there which accept additional
                //     strings like "0000-0000" as well (looking at you, Motorola...)
                if (advertiserId.isNullOrEmpty() || !advertiserId.matches(UUID_REGEX) || "00000000-0000-0000-0000-000000000000" == advertiserId) {
                    advertiserId = null
                    limitedAdTracking = true
                }

                AdvertiserIdInfoImpl(
                    if (advertiserId == null) null else UUID.fromString(advertiserId),
                    limitedAdTracking,
                )
            } catch (e: Throwable) {
                // there is not really much we can do in this case...
                logger.warn("Failed to read advertiser id", e)
                AdvertiserIdInfoImpl(null, false)
            }

        // always set the advertiser id, even if we didn't read it (as it was cached)
        // not setting it causes us to only set it on the first logger used with this method
        advertiserIdInfo.advertiserId?.let {
            logger.setAdvertiserId(it)
        }

        return advertiserIdInfo
    }

    private companion object {
        private val UUID_REGEX = Regex("^[\\da-fA-F]{8}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{12}")
    }
}
