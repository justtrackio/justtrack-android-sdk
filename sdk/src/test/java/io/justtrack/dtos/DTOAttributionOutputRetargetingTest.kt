package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionOutputRetargetingTest {
    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllFields() {
        val attributesJson = JSONObject().apply {
            put("source", "fb")
            put("medium", "cpc")
        }
        val input = JSONObject().apply {
            put("url", "https://example.com/redirect")
            put("attributes", attributesJson)
        }

        val dto = DTOAttributionOutputRetargeting(input)

        Assert.assertEquals("https://example.com/redirect", dto.url)
        Assert.assertEquals(2, dto.attributes.size)
        Assert.assertEquals("fb", dto.attributes["source"])
        Assert.assertEquals("cpc", dto.attributes["medium"])
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesEmptyAttributes() {
        val input = JSONObject().apply {
            put("url", "https://example.com")
            put("attributes", JSONObject())
        }

        val dto = DTOAttributionOutputRetargeting(input)

        Assert.assertTrue(dto.attributes.isEmpty())
    }

    @Test
    @Throws(JSONException::class)
    fun primaryConstructorStoresFields() {
        val attrs = mapOf("key" to "val")
        val dto = DTOAttributionOutputRetargeting(url = "https://example.com", attributes = attrs)

        Assert.assertEquals("https://example.com", dto.url)
        Assert.assertEquals(attrs, dto.attributes)
    }
}
