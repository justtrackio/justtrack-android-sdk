package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOSignIPResponseTest {
    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllFields() {
        val input = JSONObject().apply {
            put("ip", "192.168.1.1")
            put("type", "ipv4")
            put("token", "signed-token-abc")
        }

        val dto = DTOSignIPResponse(input)

        Assert.assertEquals("192.168.1.1", dto.ip)
        Assert.assertEquals("ipv4", dto.type)
        Assert.assertEquals("signed-token-abc", dto.token)
    }

    @Test
    @Throws(JSONException::class)
    fun primaryConstructorStoresFields() {
        val dto = DTOSignIPResponse(ip = "10.0.0.1", type = "ipv6", token = "my-token")

        Assert.assertEquals("10.0.0.1", dto.ip)
        Assert.assertEquals("ipv6", dto.type)
        Assert.assertEquals("my-token", dto.token)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorRoundTrips() {
        val original = DTOSignIPResponse(ip = "1.2.3.4", type = "ipv4", token = "tok")
        val json = JSONObject().apply {
            put("ip", original.ip)
            put("type", original.type)
            put("token", original.token)
        }
        val parsed = DTOSignIPResponse(json)

        Assert.assertEquals(original.ip, parsed.ip)
        Assert.assertEquals(original.type, parsed.type)
        Assert.assertEquals(original.token, parsed.token)
    }
}
