package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAnonymousUserTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAnonymousUser(
            installInstanceId = "install-id",
            deviceId = "device-id",
            androidId = "android-id",
        )

        Assert.assertEquals("install-id", dto.installInstanceId)
        Assert.assertEquals("device-id", dto.deviceId)
        Assert.assertEquals("android-id", dto.androidId)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAnonymousUser(
            installInstanceId = "install-id",
            deviceId = "device-id",
            androidId = "android-id",
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("install-id", json.getString("installInstanceId"))
        Assert.assertEquals("device-id", json.getString("deviceId"))
        Assert.assertEquals("android-id", json.getString("androidId"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsNullableFieldsToNullWhenAbsent() {
        val dto = DTOAnonymousUser(
            installInstanceId = "install-id",
            deviceId = null,
            androidId = null,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("install-id", json.getString("installInstanceId"))
        Assert.assertEquals(JSONObject.NULL, json["deviceId"])
        Assert.assertEquals(JSONObject.NULL, json["androidId"])
    }
}
