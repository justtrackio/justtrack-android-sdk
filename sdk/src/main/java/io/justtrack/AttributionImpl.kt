package io.justtrack

import io.justtrack.attribution.Attribution
import io.justtrack.attribution.Campaign
import io.justtrack.attribution.Channel
import io.justtrack.attribution.IdString
import io.justtrack.attribution.Partner
import java.util.Date

internal data class AttributionImpl(
    override val userType: String,
    override val campaign: Campaign,
    override val channel: Channel,
    override val partner: Partner,
    override val sourceId: String?,
    override val sourceBundleId: String?,
    override val sourcePlacement: String?,
    override val adsetId: String?,
    override val createdAt: Date,
    override val isRedownload: Boolean,
) : Attribution {

    constructor(attributionResponse: AttributionResponse) : this(
        attributionResponse.getUserType(),
        attributionResponse.getCampaign(),
        attributionResponse.getChannel(),
        attributionResponse.getPartner(),
        attributionResponse.getSourceId(),
        attributionResponse.getSourceBundleId(),
        attributionResponse.getSourcePlacement(),
        attributionResponse.getAdsetId(),
        attributionResponse.getCreatedAt(),
        attributionResponse.getRedownload(),
    )

    internal data class CampaignImpl(
        override val id: String,
        override val name: String,
        override val type: String,
        override val isOrganic: Boolean,
    ) : Campaign

    internal data class ChannelImpl(
        override val id: Int,
        override val name: String,
        override val isIncent: Boolean,
    ) : IdString, Channel

    internal data class PartnerImpl(
        override val id: Int,
        override val name: String,
    ) : IdString, Partner
}
