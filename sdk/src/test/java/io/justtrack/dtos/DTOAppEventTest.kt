package io.justtrack.dtos

import io.justtrack.ConnectionType
import io.justtrack.Formatter
import io.justtrack.events.Unit
import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import java.util.Date
import java.util.UUID

class DTOAppEventTest {
    private fun makeAppVersion() = DTOAppVersion(name = "1.0.0", code = "100")
    private fun makeSdkVersion() = DTOSdkVersion(1, 0, 0, "1.0.0", "android", null)
    private fun makeUser() = DTOAppEventUser(
        deviceId = "device-id",
        countryIso = "US",
        localeCode = "en_US",
        userId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        installInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
    )
    private fun makeDevice() = DTOAppEventDevice(
        connectionType = ConnectionType.WIFI,
        os = DTOAppEventDeviceOS(version = "13", name = "Android"),
        date = Date(1_592_228_819_000L),
    )
    private fun makeEvent(name: String = "test_event") = DTOAppEventEvent(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        name = name,
        dimensions = null,
        value = 1.0,
        unit = Unit.COUNT,
        currency = null,
        sessionId = "session-id",
        happenedAt = Date(1_592_228_819_000L),
        sequenceNumber = 1L,
    )

    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val appVersion = makeAppVersion()
        val sdkVersion = makeSdkVersion()
        val user = makeUser()
        val device = makeDevice()
        val events = listOf(makeEvent())

        val dto = DTOAppEvent(
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            user = user,
            device = device,
            events = events,
        )

        Assert.assertEquals(appVersion, dto.appVersion)
        Assert.assertEquals(sdkVersion, dto.sdkVersion)
        Assert.assertEquals(user, dto.user)
        Assert.assertEquals(device, dto.device)
        Assert.assertEquals(events, dto.events)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAppEvent(
            appVersion = makeAppVersion(),
            sdkVersion = makeSdkVersion(),
            user = makeUser(),
            device = makeDevice(),
            events = listOf(makeEvent("level_up"), makeEvent("purchase")),
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("1.0.0", json.getJSONObject("appVersion").getString("name"))
        Assert.assertEquals("100", json.getJSONObject("appVersion").getString("code"))
        Assert.assertEquals(1, json.getJSONObject("sdkVersion").getInt("major"))
        Assert.assertEquals("device-id", json.getJSONObject("user").getString("deviceId"))
        Assert.assertEquals("wifi", json.getJSONObject("device").getString("connectionType"))

        val eventsArr = json.getJSONArray("events")
        Assert.assertEquals(2, eventsArr.length())
        Assert.assertEquals("level_up", eventsArr.getJSONObject(0).getString("name"))
        Assert.assertEquals("purchase", eventsArr.getJSONObject(1).getString("name"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesEmptyEventsList() {
        val dto = DTOAppEvent(
            appVersion = makeAppVersion(),
            sdkVersion = makeSdkVersion(),
            user = makeUser(),
            device = makeDevice(),
            events = emptyList(),
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(0, json.getJSONArray("events").length())
    }
}
