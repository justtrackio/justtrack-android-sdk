package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DTOActivateExperimentsTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val ids = listOf("exp-1", "exp-2")
        val dto = DTOActivateExperiments(installInstanceId = "install-id", experimentIds = ids)

        Assert.assertEquals("install-id", dto.installInstanceId)
        Assert.assertEquals(ids, dto.experimentIds)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOActivateExperiments(
            installInstanceId = "install-id",
            experimentIds = listOf("exp-1", "exp-2", "exp-3"),
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("install-id", json.getString("installInstanceId"))
        val arr = json.getJSONArray("experimentIds")
        Assert.assertEquals(3, arr.length())
        Assert.assertEquals("exp-1", arr.getString(0))
        Assert.assertEquals("exp-2", arr.getString(1))
        Assert.assertEquals("exp-3", arr.getString(2))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesEmptyExperimentIds() {
        val dto = DTOActivateExperiments(installInstanceId = "install-id", experimentIds = emptyList())

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(0, json.getJSONArray("experimentIds").length())
    }
}
