package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DTOAppEventDeviceOSTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAppEventDeviceOS(version = "13", name = "Android")

        Assert.assertEquals("13", dto.version)
        Assert.assertEquals("Android", dto.name)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAppEventDeviceOS(version = "13", name = "Android")

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("13", json.getString("version"))
        Assert.assertEquals("Android", json.getString("name"))
    }
}
