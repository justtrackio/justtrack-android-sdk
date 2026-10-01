package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DTOAttributionInputDeviceOSTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAttributionInputDeviceOS(version = "12", name = "Android")

        Assert.assertEquals("12", dto.version)
        Assert.assertEquals("Android", dto.name)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAttributionInputDeviceOS(version = "12", name = "Android")

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("12", json.getString("version"))
        Assert.assertEquals("Android", json.getString("name"))
    }
}
