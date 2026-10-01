package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DTOPublishCustomUserIdRequestTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOPublishCustomUserIdRequest(installId = "install-123", customUserId = "user-abc")

        Assert.assertEquals("install-123", dto.installId)
        Assert.assertEquals("user-abc", dto.customUserId)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOPublishCustomUserIdRequest(installId = "install-123", customUserId = "user-abc")

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("install-123", json.getString("installId"))
        Assert.assertEquals("user-abc", json.getString("customUserId"))
    }
}
