package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionInputUserTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAttributionInputUser(
            userId = "user-1",
            customUserId = "custom-user-1",
            installInstanceId = "install-1",
            deviceId = "device-1",
            advertiserId = "advertiser-1",
            trackingId = "tracking-1",
            trackingProvider = "provider-1",
            countryIso = "DE",
            appSetId = "appset-1",
            hasLimitedAdTracking = false,
        )

        Assert.assertEquals("user-1", dto.userId)
        Assert.assertEquals("custom-user-1", dto.customUserId)
        Assert.assertEquals("install-1", dto.installInstanceId)
        Assert.assertEquals("device-1", dto.deviceId)
        Assert.assertEquals("advertiser-1", dto.advertiserId)
        Assert.assertEquals("tracking-1", dto.trackingId)
        Assert.assertEquals("provider-1", dto.trackingProvider)
        Assert.assertEquals("DE", dto.countryIso)
        Assert.assertEquals("appset-1", dto.appSetId)
        Assert.assertFalse(dto.hasLimitedAdTracking)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAttributionInputUser(
            userId = "user-1",
            customUserId = "custom-user-1",
            installInstanceId = "install-1",
            deviceId = "device-1",
            advertiserId = "advertiser-1",
            trackingId = "tracking-1",
            trackingProvider = "provider-1",
            countryIso = "DE",
            appSetId = "appset-1",
            hasLimitedAdTracking = true,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("user-1", json.getString("userId"))
        Assert.assertEquals("custom-user-1", json.getString("customUserId"))
        Assert.assertEquals("install-1", json.getString("installInstanceId"))
        Assert.assertEquals("device-1", json.getString("deviceId"))
        Assert.assertEquals("advertiser-1", json.getString("advertiserId"))
        Assert.assertEquals("tracking-1", json.getString("trackingId"))
        Assert.assertEquals("provider-1", json.getString("trackingProvider"))
        Assert.assertEquals("DE", json.getString("countryIso"))
        Assert.assertEquals("appset-1", json.getString("appSetId"))
        Assert.assertTrue(json.getBoolean("hasLimitedAdTracking"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsNullableFieldsToNullWhenAbsent() {
        val dto = DTOAttributionInputUser(
            userId = null,
            customUserId = null,
            installInstanceId = "install-1",
            deviceId = "device-1",
            advertiserId = null,
            trackingId = null,
            trackingProvider = "provider-1",
            countryIso = null,
            appSetId = null,
            hasLimitedAdTracking = false,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["userId"])
        Assert.assertEquals(JSONObject.NULL, json["customUserId"])
        Assert.assertEquals(JSONObject.NULL, json["advertiserId"])
        Assert.assertEquals(JSONObject.NULL, json["trackingId"])
        Assert.assertEquals(JSONObject.NULL, json["countryIso"])
        Assert.assertEquals(JSONObject.NULL, json["appSetId"])
    }
}
