package io.justtrack.dtos

import io.justtrack.ConnectionType
import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DTOAppEventDeviceTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val os = DTOAppEventDeviceOS(version = "13", name = "Android")
        val date = Date(1_592_228_819_000L)
        val dto = DTOAppEventDevice(
            connectionType = ConnectionType.WIFI,
            os = os,
            date = date,
        )

        Assert.assertEquals(ConnectionType.WIFI, dto.connectionType)
        Assert.assertEquals(os, dto.os)
        Assert.assertEquals(date, dto.date)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val os = DTOAppEventDeviceOS(version = "13", name = "Android")
        val date = Date(1_592_228_819_000L)
        val dto = DTOAppEventDevice(
            connectionType = ConnectionType.WIFI,
            os = os,
            date = date,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("wifi", json.getString("connectionType"))
        Assert.assertEquals("13", json.getJSONObject("os").getString("version"))
        Assert.assertEquals("Android", json.getJSONObject("os").getString("name"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(date), json.getString("date"))
    }
}
