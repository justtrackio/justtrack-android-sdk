package io.justtrack

import io.justtrack.PublishEventsQueue.Companion.build
import io.justtrack.events.Unit
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.SdkVersionImpl
import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import java.text.ParseException
import java.util.UUID

internal class AppEventDTOTest : BaseDTOTest() {
    @Test
    @Throws(JSONException::class, ParseException::class)
    fun serializeInput() {
        val sdkVersion: SdkVersion = SdkVersionImpl(7, 12, 8, "7.12.8-test1")
        val applicationVersion = ApplicationVersionImpl("2.1.0", "21")
        val userEvent = AppEvent("test_event_action", 3.0, Unit.SECONDS)
            .addDimension("custom_1", "dim1")
            .addDimension("custom_2", "dim2")
            .addDimension("custom_3", "dim3")
            .build("session id", sdkVersion)

        val eventId = UUID.randomUUID()
        val publishingEvent = StorableEvent(1, eventId, userEvent, 1)
        val json = build(
            listOf(publishingEvent),
            deviceInfoImpl,
            PublishEventsQueue.DTOBuildAttributionParams(
                "advertiser id",
                "tracking id",
                "tracking provider",
                UUID.fromString("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e"),
                UUID.fromString("f7642b2f-35e9-4a2f-9c26-a5f9cd2b4794"),
            ),
            sdkVersion,
            applicationVersion,
        ).toJSON(Formatter)

        val appVersion = json.getJSONObject("appVersion")
        Assert.assertEquals("21", appVersion.getString("code"))
        Assert.assertEquals("2.1.0", appVersion.getString("name"))

        val sdkVersionJson = json.getJSONObject("sdkVersion")
        Assert.assertEquals(sdkVersion.major.toLong(), sdkVersionJson.getInt("major").toLong())
        Assert.assertEquals(sdkVersion.minor.toLong(), sdkVersionJson.getInt("minor").toLong())
        Assert.assertEquals(sdkVersion.patch.toLong(), sdkVersionJson.getInt("patch").toLong())
        Assert.assertEquals(sdkVersion.name, sdkVersionJson.getString("name"))

        val user = json.getJSONObject("user")
        Assert.assertEquals("advertiser id", user.getString("deviceId"))
        Assert.assertEquals("country iso", user.getString("countryIso"))
        Assert.assertEquals("device locale", user.getString("localeCode"))
        Assert.assertEquals("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e", user.getString("userId"))

        val device = json.getJSONObject("device")
        Assert.assertEquals(ConnectionType.CELLULAR_5G.toString(), device.getString("connectionType"))
        val clientDate = Formatter.parseDate(device.getString("date"))
        Assert.assertEquals(clientDate.time.toDouble(), System.currentTimeMillis().toDouble(), 3000.0)

        val os = device.getJSONObject("os")
        Assert.assertEquals("os version", os.getString("version"))
        Assert.assertEquals("Android", os.getString("name"))

        val events = json.getJSONArray("events")
        Assert.assertEquals(1, events.length().toLong())

        val event = events.getJSONObject(0)
        Assert.assertEquals(eventId, UUID.fromString(event.getString("id")))
        Assert.assertEquals("test_event_action", event.getString("name"))
        val dimensions = event.getJSONObject("dimensions")
        Assert.assertEquals("dim1", dimensions.getString("custom_1"))
        Assert.assertEquals("dim2", dimensions.getString("custom_2"))
        Assert.assertEquals("dim3", dimensions.getString("custom_3"))
        Assert.assertEquals(3000.0, event.getDouble("value"), 0.0)
        Assert.assertEquals("milliseconds", event.getString("unit"))
        val happenedAt = Formatter.parseDate(event.getString("happenedAt"))
        Assert.assertEquals(happenedAt.time.toFloat(), clientDate.time.toFloat(), 1000f)
        Assert.assertEquals(happenedAt.time.toDouble(), System.currentTimeMillis().toDouble(), 2000.0)
        Assert.assertEquals(userEvent.happenedAt.time.toFloat(), System.currentTimeMillis().toFloat(), 2000f)
    }
}
