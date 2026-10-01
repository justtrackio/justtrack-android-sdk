package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionOutputAttributionNetworkTest {
    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllFields() {
        val input = JSONObject().apply {
            put("id", 99)
            put("name", "network-name")
        }

        val dto = DTOAttributionOutputAttributionNetwork(input)

        Assert.assertEquals(99, dto.id)
        Assert.assertEquals("network-name", dto.name)
    }

    @Test
    @Throws(JSONException::class)
    fun primaryConstructorStoresFields() {
        val dto = DTOAttributionOutputAttributionNetwork(id = 11, name = "net")

        Assert.assertEquals(11, dto.id)
        Assert.assertEquals("net", dto.name)
    }
}
