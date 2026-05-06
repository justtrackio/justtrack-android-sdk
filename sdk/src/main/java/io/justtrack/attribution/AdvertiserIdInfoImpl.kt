package io.justtrack.attribution

import java.util.UUID

internal class AdvertiserIdInfoImpl internal constructor(
    private val pAdvertiserId: UUID?,
    override val isLimitedAdTracking: Boolean,
) : AdvertiserIdInfo {
    override val advertiserId: String?
        get() = pAdvertiserId?.toString()?.lowercase()
}
