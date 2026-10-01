package io.justtrack.ads

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.justtrack.attribution.AdvertiserIdInfo

internal class DeviceAdvertiserIdReaderImpl internal constructor(private val context: Context) : DeviceAdvertiserIdReader {
    override fun readAdvertiserId(): AdvertiserIdInfo {
        val advertiserIdInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
        val info = object : AdvertiserIdInfo {
            override val advertiserId: String?
                get() = advertiserIdInfo.id
            override val isLimitedAdTracking: Boolean
                get() = advertiserIdInfo.isLimitAdTrackingEnabled
        }

        return info
    }
}
