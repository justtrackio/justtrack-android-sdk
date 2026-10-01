package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionOutputUserTest {
    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllFields() {
        val input = JSONObject().apply {
            put("installId", "install-123")
            put("type", "new")
            put("redownload", false)
        }

        val dto = DTOAttributionOutputUser(input)

        Assert.assertEquals("install-123", dto.installId)
        Assert.assertEquals("new", dto.type)
        Assert.assertFalse(dto.redownload)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorHandlesNullTestGroup() {
        val input = JSONObject().apply {
            put("installId", "install-456")
            put("type", "returning")
            put("redownload", true)
        }

        val dto = DTOAttributionOutputUser(input)

        Assert.assertTrue(dto.redownload)
    }

    @Test
    @Throws(JSONException::class)
    fun primaryConstructorStoresFields() {
        val dto = DTOAttributionOutputUser(
            installId = "iid",
            type = "new",
            redownload = false,
        )

        Assert.assertEquals("iid", dto.installId)
        Assert.assertEquals("new", dto.type)
        Assert.assertFalse(dto.redownload)
    }
}
