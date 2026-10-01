package io.justtrack.dtos

import io.justtrack.DeviceType
import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DTOAttributionInputDeviceTest {
    private fun makeOs() = DTOAttributionInputDeviceOS(version = "11", name = "Android")
    private fun makeDisplay() = DTOAttributionInputDeviceDisplay(width = 1080, height = 1920)

    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val os = makeOs()
        val display = makeDisplay()
        val dto = DTOAttributionInputDevice(
            name = "Pixel 6",
            model = "Pixel 6",
            product = "raven",
            type = DeviceType.PHONE,
            os = os,
            display = display,
        )

        Assert.assertEquals("Pixel 6", dto.name)
        Assert.assertEquals("Pixel 6", dto.model)
        Assert.assertEquals("raven", dto.product)
        Assert.assertEquals(DeviceType.PHONE, dto.type)
        Assert.assertEquals(os, dto.os)
        Assert.assertEquals(display, dto.display)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAttributionInputDevice(
            name = "Pixel 6",
            model = "Pixel 6",
            product = "raven",
            type = DeviceType.PHONE,
            os = makeOs(),
            display = makeDisplay(),
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("Pixel 6", json.getString("name"))
        Assert.assertEquals("Pixel 6", json.getString("model"))
        Assert.assertEquals("raven", json.getString("product"))
        Assert.assertEquals("phone", json.getString("type"))
        Assert.assertEquals("11", json.getJSONObject("os").getString("version"))
        Assert.assertEquals("Android", json.getJSONObject("os").getString("name"))
        Assert.assertEquals(1080, json.getJSONObject("display").getInt("width"))
        Assert.assertEquals(1920, json.getJSONObject("display").getInt("height"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesTabletType() {
        val dto = DTOAttributionInputDevice(
            name = "Tab S8",
            model = "Tab S8",
            product = "gts8",
            type = DeviceType.TABLET,
            os = makeOs(),
            display = makeDisplay(),
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("tablet", json.getString("type"))
    }
}
