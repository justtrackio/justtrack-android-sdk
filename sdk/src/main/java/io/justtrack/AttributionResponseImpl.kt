package io.justtrack

import io.justtrack.attribution.Campaign
import io.justtrack.attribution.Channel
import io.justtrack.attribution.Partner
import java.util.Date
import java.util.UUID

internal data class AttributionResponseImpl(
    private val userId: UUID,
    private val installId: String,
    private val userType: String,
    private val campaign: Campaign,
    private val channel: Channel,
    private val partner: Partner,
    private val sourceId: String?,
    private val sourceBundleId: String?,
    private val sourcePlacement: String?,
    private val adsetId: String?,
    private val createdAt: Date,
    private val redownload: Boolean,
) : AttributionResponse {
    override fun getUserId(): UUID = userId
    override fun getInstallId(): String = installId
    override fun getUserType(): String = userType
    override fun getCampaign(): Campaign = campaign
    override fun getChannel(): Channel = channel
    override fun getPartner(): Partner = partner
    override fun getSourceId(): String? = sourceId
    override fun getSourceBundleId(): String? = sourceBundleId
    override fun getSourcePlacement(): String? = sourcePlacement
    override fun getAdsetId(): String? = adsetId
    override fun getCreatedAt(): Date = createdAt
    override fun getRedownload(): Boolean = redownload
}
