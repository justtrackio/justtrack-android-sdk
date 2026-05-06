package io.justtrack.providers

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.justtrack.attribution.AdvertiserIdInfo

internal class AdvertiserIdProviderImpl internal constructor(val context: Context) : AdvertiserIdProvider {
    override fun provideAdvertiserId(): AdvertiserIdInfo {
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
