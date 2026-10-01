package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOPublishFirebaseAppInstanceIdRequestTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOPublishFirebaseAppInstanceIdRequest(
            uuid = "uuid-123",
            firebaseInstanceId = "firebase-instance-id",
        )

        Assert.assertEquals("uuid-123", dto.uuid)
        Assert.assertEquals("firebase-instance-id", dto.firebaseInstanceId)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesFields() {
        val input = JSONObject().apply {
            put("uuid", "uuid-456")
            put("firebaseInstanceId", "firebase-456")
        }

        val dto = DTOPublishFirebaseAppInstanceIdRequest(input)

        Assert.assertEquals("uuid-456", dto.uuid)
        Assert.assertEquals("firebase-456", dto.firebaseInstanceId)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOPublishFirebaseAppInstanceIdRequest(
            uuid = "uuid-123",
            firebaseInstanceId = "firebase-instance-id",
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("uuid-123", json.getString("uuid"))
        Assert.assertEquals("firebase-instance-id", json.getString("firebaseInstanceId"))
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorRoundTripsViaToJson() {
        val dto = DTOPublishFirebaseAppInstanceIdRequest(uuid = "u1", firebaseInstanceId = "fid1")
        val json = dto.toJSON(Formatter)
        val roundTripped = DTOPublishFirebaseAppInstanceIdRequest(json)

        Assert.assertEquals(dto.uuid, roundTripped.uuid)
        Assert.assertEquals(dto.firebaseInstanceId, roundTripped.firebaseInstanceId)
    }
}
