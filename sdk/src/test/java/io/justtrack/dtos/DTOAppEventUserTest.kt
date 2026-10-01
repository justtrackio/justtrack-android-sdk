package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.UUID

class DTOAppEventUserTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val installInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val dto = DTOAppEventUser(
            deviceId = "device-id",
            countryIso = "DE",
            localeCode = "de_DE",
            userId = userId,
            installInstanceId = installInstanceId,
        )

        Assert.assertEquals("device-id", dto.deviceId)
        Assert.assertEquals("DE", dto.countryIso)
        Assert.assertEquals("de_DE", dto.localeCode)
        Assert.assertEquals(userId, dto.userId)
        Assert.assertEquals(installInstanceId, dto.installInstanceId)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val installInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val dto = DTOAppEventUser(
            deviceId = "device-id",
            countryIso = "DE",
            localeCode = "de_DE",
            userId = userId,
            installInstanceId = installInstanceId,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("device-id", json.getString("deviceId"))
        Assert.assertEquals("DE", json.getString("countryIso"))
        Assert.assertEquals("de_DE", json.getString("localeCode"))
        Assert.assertEquals(userId.toString(), json.getString("userId"))
        Assert.assertEquals(installInstanceId.toString(), json.getString("installInstanceId"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsNullableFieldsToNullWhenAbsent() {
        val dto = DTOAppEventUser(
            deviceId = null,
            countryIso = null,
            localeCode = null,
            userId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            installInstanceId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["deviceId"])
        Assert.assertEquals(JSONObject.NULL, json["countryIso"])
        Assert.assertEquals(JSONObject.NULL, json["localeCode"])
    }
}
