package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DTOAppVersionTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAppVersion(name = "1.2.3", code = "123")

        Assert.assertEquals("1.2.3", dto.name)
        Assert.assertEquals("123", dto.code)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAppVersion(name = "2.0.0", code = "200")

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("2.0.0", json.getString("name"))
        Assert.assertEquals("200", json.getString("code"))
    }
}
