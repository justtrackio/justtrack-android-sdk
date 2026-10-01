package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOSdkVersionTest {
    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dto = DTOSdkVersion(
            major = 1,
            minor = 2,
            patch = 3,
            name = "1.2.3",
            platform = "android",
            wrapper = "flutter",
        )

        Assert.assertEquals(1, dto.major)
        Assert.assertEquals(2, dto.minor)
        Assert.assertEquals(3, dto.patch)
        Assert.assertEquals("1.2.3", dto.name)
        Assert.assertEquals("android", dto.platform)
        Assert.assertEquals("flutter", dto.wrapper)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOSdkVersion(
            major = 7,
            minor = 12,
            patch = 8,
            name = "7.12.8",
            platform = "android",
            wrapper = "unity",
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(7, json.getInt("major"))
        Assert.assertEquals(12, json.getInt("minor"))
        Assert.assertEquals(8, json.getInt("patch"))
        Assert.assertEquals("7.12.8", json.getString("name"))
        Assert.assertEquals("android", json.getString("platform"))
        Assert.assertEquals("unity", json.getString("wrapper"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsWrapperToNullWhenAbsent() {
        val dto = DTOSdkVersion(
            major = 1,
            minor = 0,
            patch = 0,
            name = "1.0.0",
            platform = "android",
            wrapper = null,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["wrapper"])
    }
}
