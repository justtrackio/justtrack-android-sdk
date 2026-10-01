package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionOutputAttributionChannelTest {
    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllFields() {
        val input = JSONObject().apply {
            put("id", 7)
            put("name", "channel-name")
            put("incent", true)
        }

        val dto = DTOAttributionOutputAttributionChannel(input)

        Assert.assertEquals(7, dto.id)
        Assert.assertEquals("channel-name", dto.name)
        Assert.assertTrue(dto.incent)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesIncentFalse() {
        val input = JSONObject().apply {
            put("id", 3)
            put("name", "non-incent")
            put("incent", false)
        }

        val dto = DTOAttributionOutputAttributionChannel(input)

        Assert.assertFalse(dto.incent)
    }

    @Test
    @Throws(JSONException::class)
    fun primaryConstructorStoresFields() {
        val dto = DTOAttributionOutputAttributionChannel(id = 5, name = "ch", incent = false)

        Assert.assertEquals(5, dto.id)
        Assert.assertEquals("ch", dto.name)
        Assert.assertFalse(dto.incent)
    }
}
