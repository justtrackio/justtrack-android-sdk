package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionOutputAttributionTest {
    private fun makeCampaignJson() = JSONObject().apply {
        put("externalId", "1")
        put("name", "camp")
        put("type", "cpi")
        put("organic", false)
    }

    private fun makeChannelJson() = JSONObject().apply {
        put("id", 2)
        put("name", "ch")
        put("incent", false)
    }

    private fun makeNetworkJson() = JSONObject().apply {
        put("id", 3)
        put("name", "net")
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllPresentFields() {
        val attributedAt = "2020-06-15T13:46:59.000Z"
        val input = JSONObject().apply {
            put("campaign", makeCampaignJson())
            put("channel", makeChannelJson())
            put("network", makeNetworkJson())
            put("sourceId", "src-1")
            put("sourceBundleId", "bundle-1")
            put("sourcePlacement", "placement-1")
            put("adsetId", "adset-1")
            put("attributedAt", attributedAt)
        }

        val dto = DTOAttributionOutputAttribution(input, Formatter)

        Assert.assertEquals("1", dto.campaign.externalId)
        Assert.assertEquals("camp", dto.campaign.name)
        Assert.assertEquals(2, dto.channel.id)
        Assert.assertEquals(3, dto.network.id)
        Assert.assertEquals("src-1", dto.sourceId)
        Assert.assertEquals("bundle-1", dto.sourceBundleId)
        Assert.assertEquals("placement-1", dto.sourcePlacement)
        Assert.assertEquals("adset-1", dto.adsetId)
        Assert.assertEquals(Formatter.parseDate(attributedAt), dto.attributedAt)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorHandlesNullOptionalFields() {
        val input = JSONObject().apply {
            put("campaign", makeCampaignJson())
            put("channel", makeChannelJson())
            put("network", makeNetworkJson())
            put("sourceId", JSONObject.NULL)
            put("sourceBundleId", JSONObject.NULL)
            put("sourcePlacement", JSONObject.NULL)
            put("adsetId", JSONObject.NULL)
            put("attributedAt", "2020-06-15T13:46:59.000Z")
        }

        val dto = DTOAttributionOutputAttribution(input, Formatter)

        Assert.assertNull(dto.sourceId)
        Assert.assertNull(dto.sourceBundleId)
        Assert.assertNull(dto.sourcePlacement)
        Assert.assertNull(dto.adsetId)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorHandlesMissingOptionalFields() {
        val input = JSONObject().apply {
            put("campaign", makeCampaignJson())
            put("channel", makeChannelJson())
            put("network", makeNetworkJson())
            put("attributedAt", "2020-06-15T13:46:59.000Z")
        }

        val dto = DTOAttributionOutputAttribution(input, Formatter)

        Assert.assertNull(dto.sourceId)
        Assert.assertNull(dto.sourceBundleId)
        Assert.assertNull(dto.sourcePlacement)
        Assert.assertNull(dto.adsetId)
    }
}
