package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionOutputAttributionCampaignTest {
    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllFields() {
        val input = JSONObject().apply {
            put("externalId", "42")
            put("name", "campaign-name")
            put("type", "cpi")
            put("organic", false)
        }

        val dto = DTOAttributionOutputAttributionCampaign(input)

        Assert.assertEquals("42", dto.externalId)
        Assert.assertEquals("campaign-name", dto.name)
        Assert.assertEquals("cpi", dto.type)
        Assert.assertFalse(dto.organic)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesOrganicTrue() {
        val input = JSONObject().apply {
            put("externalId", "1")
            put("name", "organic-campaign")
            put("type", "organic")
            put("organic", true)
        }

        val dto = DTOAttributionOutputAttributionCampaign(input)

        Assert.assertTrue(dto.organic)
    }

    @Test
    @Throws(JSONException::class)
    fun primaryConstructorStoresFields() {
        val dto = DTOAttributionOutputAttributionCampaign(externalId = "10", name = "test", type = "cpm", organic = true)

        Assert.assertEquals("10", dto.externalId)
        Assert.assertEquals("test", dto.name)
        Assert.assertEquals("cpm", dto.type)
        Assert.assertTrue(dto.organic)
    }

    @Test(expected = JSONException::class)
    fun jsonConstructorThrowsOnNullExternalId() {
        val input = JSONObject().apply {
            put("externalId", JSONObject.NULL)
            put("name", "campaign-name")
            put("type", "cpi")
            put("organic", false)
        }

        DTOAttributionOutputAttributionCampaign(input)
    }

    @Test(expected = JSONException::class)
    fun jsonConstructorThrowsOnMissingExternalId() {
        val input = JSONObject().apply {
            put("name", "campaign-name")
            put("type", "cpi")
            put("organic", false)
        }

        DTOAttributionOutputAttributionCampaign(input)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorAcceptsEmptyExternalId() {
        val input = JSONObject().apply {
            put("externalId", "")
            put("name", "campaign-name")
            put("type", "cpi")
            put("organic", false)
        }

        val dto = DTOAttributionOutputAttributionCampaign(input)

        Assert.assertEquals("", dto.externalId)
    }
}
