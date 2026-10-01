package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DTOAttributionInputReferrerTest {
    private val clickDate = Date(1_592_228_819_000L)
    private val installBeginDate = Date(1_592_228_820_000L)
    private val clientDate = Date(1_592_228_821_000L)
    private val serverClickDate = Date(1_592_228_822_000L)
    private val serverInstallBeginDate = Date(1_592_228_823_000L)

    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAttributionInputReferrer(
            value = "utm_source=network",
            clickDate = clickDate,
            installBeginDate = installBeginDate,
            clientDate = clientDate,
            serverClickDate = serverClickDate,
            serverInstallBeginDate = serverInstallBeginDate,
            installVersion = "2.0.0",
        )

        Assert.assertEquals("utm_source=network", dto.value)
        Assert.assertEquals(clickDate, dto.clickDate)
        Assert.assertEquals(installBeginDate, dto.installBeginDate)
        Assert.assertEquals(clientDate, dto.clientDate)
        Assert.assertEquals(serverClickDate, dto.serverClickDate)
        Assert.assertEquals(serverInstallBeginDate, dto.serverInstallBeginDate)
        Assert.assertEquals("2.0.0", dto.installVersion)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAttributionInputReferrer(
            value = "utm_source=network",
            clickDate = clickDate,
            installBeginDate = installBeginDate,
            clientDate = clientDate,
            serverClickDate = serverClickDate,
            serverInstallBeginDate = serverInstallBeginDate,
            installVersion = "2.0.0",
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("utm_source=network", json.getString("value"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(clickDate), json.getString("clickDate"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(installBeginDate), json.getString("installBeginDate"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(clientDate), json.getString("clientDate"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(serverClickDate), json.getString("serverClickDate"))
        Assert.assertEquals(
            Formatter.formatDateMilliseconds(serverInstallBeginDate),
            json.getString("serverInstallBeginDate"),
        )
        Assert.assertEquals("2.0.0", json.getString("installVersion"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsInstallVersionToNullWhenAbsent() {
        val dto = DTOAttributionInputReferrer(
            value = "utm_source=network",
            clickDate = clickDate,
            installBeginDate = installBeginDate,
            clientDate = clientDate,
            serverClickDate = serverClickDate,
            serverInstallBeginDate = serverInstallBeginDate,
            installVersion = null,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["installVersion"])
    }
}
