package io.justtrack.providers

import io.justtrack.attribution.AdvertiserIdInfo

internal interface AdvertiserIdProvider {
    fun provideAdvertiserId(): AdvertiserIdInfo
}
