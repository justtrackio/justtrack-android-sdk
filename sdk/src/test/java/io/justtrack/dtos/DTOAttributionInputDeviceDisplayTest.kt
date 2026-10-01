package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DTOAttributionInputDeviceDisplayTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAttributionInputDeviceDisplay(width = 1920, height = 1080)

        Assert.assertEquals(1920, dto.width)
        Assert.assertEquals(1080, dto.height)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAttributionInputDeviceDisplay(width = 1920, height = 1080)

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(1920, json.getInt("width"))
        Assert.assertEquals(1080, json.getInt("height"))
    }
}
