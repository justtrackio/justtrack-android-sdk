package io.justtrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

internal class AttributionImplTest {
    @Test
    fun `exposes constructor fields and data classes`() {
        val campaign = AttributionImpl.CampaignImpl("1", "campaign", "acquisition", isOrganic = true)
        val channel = AttributionImpl.ChannelImpl(2, "channel", isIncent = true)
        val partner = AttributionImpl.PartnerImpl(3, "partner")
        val createdAt = Date(0L)
        val attribution = AttributionImpl(
            "retargeting",
            campaign,
            channel,
            partner,
            "source",
            "bundle",
            "placement",
            "adset",
            createdAt,
            isRedownload = true,
        )

        assertEquals("retargeting", attribution.userType)
        assertSame(campaign, attribution.campaign)
        assertSame(channel, attribution.channel)
        assertSame(partner, attribution.partner)
        assertEquals("source", attribution.sourceId)
        assertEquals("bundle", attribution.sourceBundleId)
        assertEquals("placement", attribution.sourcePlacement)
        assertEquals("adset", attribution.adsetId)
        assertEquals(createdAt, attribution.createdAt)
        assertTrue(attribution.isRedownload)
        assertEquals(attribution, attribution.copy())
        assertEquals(campaign, campaign.copy())
        assertEquals(channel, channel.copy())
        assertEquals(partner, partner.copy())
    }
}
