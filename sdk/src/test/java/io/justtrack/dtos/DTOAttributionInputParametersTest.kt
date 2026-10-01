package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionInputParametersTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOAttributionInputParameters(
            installSource = "play_store",
            integritySecret = "secret",
        )

        Assert.assertEquals("play_store", dto.installSource)
        Assert.assertEquals("secret", dto.integritySecret)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAttributionInputParameters(
            installSource = "play_store",
            integritySecret = "secret",
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("play_store", json.getString("installSource"))
        Assert.assertEquals("secret", json.getString("integritySecret"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsNullableFieldsToNullWhenAbsent() {
        val dto = DTOAttributionInputParameters(
            installSource = null,
            integritySecret = null,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["installSource"])
        Assert.assertEquals(JSONObject.NULL, json["integritySecret"])
    }
}
