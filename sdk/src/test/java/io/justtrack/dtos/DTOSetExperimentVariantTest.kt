package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DTOSetExperimentVariantTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val happenedAt = Date(1_592_228_819_000L)
        val tags = listOf("tag1", "tag2")
        val dto = DTOSetExperimentVariant(
            installInstanceId = "install-id",
            justtrackSdkVersion = "7.12.8",
            appVersionName = "2.0.0",
            appVersionCode = "200",
            osVersion = "13",
            experiment = "exp-a",
            variant = "variant-b",
            tags = tags,
            happenedAt = happenedAt,
        )

        Assert.assertEquals("install-id", dto.installInstanceId)
        Assert.assertEquals("7.12.8", dto.justtrackSdkVersion)
        Assert.assertEquals("2.0.0", dto.appVersionName)
        Assert.assertEquals("200", dto.appVersionCode)
        Assert.assertEquals("13", dto.osVersion)
        Assert.assertEquals("exp-a", dto.experiment)
        Assert.assertEquals("variant-b", dto.variant)
        Assert.assertEquals(tags, dto.tags)
        Assert.assertEquals(happenedAt, dto.happenedAt)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val happenedAt = Date(1_592_228_819_000L)
        val dto = DTOSetExperimentVariant(
            installInstanceId = "install-id",
            justtrackSdkVersion = "7.12.8",
            appVersionName = "2.0.0",
            appVersionCode = "200",
            osVersion = "13",
            experiment = "exp-a",
            variant = "variant-b",
            tags = listOf("t1", "t2"),
            happenedAt = happenedAt,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("install-id", json.getString("installInstanceId"))
        Assert.assertEquals("7.12.8", json.getString("justtrackSdkVersion"))
        Assert.assertEquals("2.0.0", json.getString("appVersionName"))
        Assert.assertEquals("200", json.getString("appVersionCode"))
        Assert.assertEquals("13", json.getString("osVersion"))
        Assert.assertEquals("exp-a", json.getString("experiment"))
        Assert.assertEquals("variant-b", json.getString("variant"))
        val tagsArr = json.getJSONArray("tags")
        Assert.assertEquals(2, tagsArr.length())
        Assert.assertEquals("t1", tagsArr.getString(0))
        Assert.assertEquals("t2", tagsArr.getString(1))
        Assert.assertEquals(Formatter.formatDateMilliseconds(happenedAt), json.getString("happenedAt"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsTagsAndHappenedAtToNullWhenAbsent() {
        val dto = DTOSetExperimentVariant(
            installInstanceId = "install-id",
            justtrackSdkVersion = "7.12.8",
            appVersionName = "2.0.0",
            appVersionCode = "200",
            osVersion = "13",
            experiment = "exp-a",
            variant = "variant-b",
            tags = null,
            happenedAt = null,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["tags"])
        Assert.assertEquals(JSONObject.NULL, json["happenedAt"])
    }
}
