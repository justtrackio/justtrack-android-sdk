package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOIntegrityTokenTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOIntegrityToken(
            integrityToken = "token-abc",
            installInstanceId = "install-id",
            errorCode = null,
            errorMessage = null,
        )

        Assert.assertEquals("token-abc", dto.integrityToken)
        Assert.assertEquals("install-id", dto.installInstanceId)
        Assert.assertNull(dto.errorCode)
        Assert.assertNull(dto.errorMessage)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllPresentFields() {
        val input = JSONObject().apply {
            put("integrityToken", "token-xyz")
            put("installInstanceId", "install-99")
            put("errorCode", 42)
            put("errorMessage", "something went wrong")
        }

        val dto = DTOIntegrityToken(input)

        Assert.assertEquals("token-xyz", dto.integrityToken)
        Assert.assertEquals("install-99", dto.installInstanceId)
        Assert.assertEquals(42, dto.errorCode)
        Assert.assertEquals("something went wrong", dto.errorMessage)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorHandlesNullFields() {
        val input = JSONObject().apply {
            put("integrityToken", JSONObject.NULL)
            put("installInstanceId", "install-99")
            put("errorCode", JSONObject.NULL)
            put("errorMessage", JSONObject.NULL)
        }

        val dto = DTOIntegrityToken(input)

        Assert.assertNull(dto.integrityToken)
        Assert.assertNull(dto.errorCode)
        Assert.assertNull(dto.errorMessage)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorHandlesMissingOptionalFields() {
        val input = JSONObject().apply {
            put("installInstanceId", "install-99")
        }

        val dto = DTOIntegrityToken(input)

        Assert.assertNull(dto.integrityToken)
        Assert.assertNull(dto.errorCode)
        Assert.assertNull(dto.errorMessage)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOIntegrityToken(
            integrityToken = "token-abc",
            installInstanceId = "install-id",
            errorCode = 10,
            errorMessage = "error msg",
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("token-abc", json.getString("integrityToken"))
        Assert.assertEquals("install-id", json.getString("installInstanceId"))
        Assert.assertEquals(10, json.getInt("errorCode"))
        Assert.assertEquals("error msg", json.getString("errorMessage"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsNullableFieldsToNullWhenAbsent() {
        val dto = DTOIntegrityToken(
            integrityToken = null,
            installInstanceId = "install-id",
            errorCode = null,
            errorMessage = null,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["integrityToken"])
        Assert.assertEquals(JSONObject.NULL, json["errorCode"])
        Assert.assertEquals(JSONObject.NULL, json["errorMessage"])
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorRoundTripsViaToJson() {
        val dto = DTOIntegrityToken(
            integrityToken = "tok",
            installInstanceId = "iid",
            errorCode = 5,
            errorMessage = "err",
        )
        val json = dto.toJSON(Formatter)
        val roundTripped = DTOIntegrityToken(json)

        Assert.assertEquals(dto.integrityToken, roundTripped.integrityToken)
        Assert.assertEquals(dto.installInstanceId, roundTripped.installInstanceId)
        Assert.assertEquals(dto.errorCode, roundTripped.errorCode)
        Assert.assertEquals(dto.errorMessage, roundTripped.errorMessage)
    }
}
