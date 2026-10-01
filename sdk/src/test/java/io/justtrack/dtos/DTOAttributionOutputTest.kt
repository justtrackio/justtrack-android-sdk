package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class DTOAttributionOutputTest {
    private fun makeUserJson() = JSONObject().apply {
        put("installId", "install-1")
        put("type", "new")
        put("testGroup", JSONObject.NULL)
        put("redownload", false)
    }

    private fun makeAttributionJson() = JSONObject().apply {
        put(
            "campaign",
            JSONObject().apply {
                put("externalId", "1")
                put("name", "camp")
                put("type", "cpi")
                put("organic", false)
            },
        )
        put(
            "channel",
            JSONObject().apply {
                put("id", 2)
                put("name", "ch")
                put("incent", false)
            },
        )
        put(
            "network",
            JSONObject().apply {
                put("id", 3)
                put("name", "net")
            },
        )
        put("attributedAt", "2020-06-15T13:46:59.000Z")
    }

    private fun makeRetargetingJson() = JSONObject().apply {
        put("url", "https://example.com")
        put("attributes", JSONObject())
    }

    private fun makeSdkConfigJson() = JSONObject().apply {
        put(
            "log",
            JSONObject().apply {
                put("rules", JSONArray())
                put("logPercentage", JSONObject())
            },
        )
        put("metric", JSONObject().apply { put("rules", JSONArray()) })
        put("event", JSONObject().apply { put("rules", JSONArray()) })
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesAllPresentFields() {
        val input = JSONObject().apply {
            put("user", makeUserJson())
            put("attribution", makeAttributionJson())
            put("retargeting", makeRetargetingJson())
            put("sdkConfig", makeSdkConfigJson())
        }

        val dto = DTOAttributionOutput(input, Formatter)

        Assert.assertEquals("install-1", dto.user.installId)
        Assert.assertNotNull(dto.retargeting)
        Assert.assertEquals("https://example.com", dto.retargeting!!.url)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorHandlesNullRetargetingAndSdkConfig() {
        val input = JSONObject().apply {
            put("user", makeUserJson())
            put("attribution", makeAttributionJson())
            put("retargeting", JSONObject.NULL)
            put("sdkConfig", JSONObject.NULL)
        }

        val dto = DTOAttributionOutput(input, Formatter)

        Assert.assertNull(dto.retargeting)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorHandlesMissingRetargetingAndSdkConfig() {
        val input = JSONObject().apply {
            put("user", makeUserJson())
            put("attribution", makeAttributionJson())
        }

        val dto = DTOAttributionOutput(input, Formatter)

        Assert.assertNull(dto.retargeting)
    }

    @Test
    @Throws(JSONException::class)
    fun primaryConstructorStoresFields() {
        val user = DTOAttributionOutputUser(installId = "iid", type = "new", redownload = false)
        val attribution = DTOAttributionOutputAttribution(
            campaign = DTOAttributionOutputAttributionCampaign("1", "c", "t", false),
            channel = DTOAttributionOutputAttributionChannel(2, "ch", false),
            network = DTOAttributionOutputAttributionNetwork(3, "n"),
            sourceId = null,
            sourceBundleId = null,
            sourcePlacement = null,
            adsetId = null,
            attributedAt = Formatter.parseDate("2020-06-15T13:46:59.000Z"),
        )
        val dto = DTOAttributionOutput(
            user = user,
            attribution = attribution,
            retargeting = null,
        )

        Assert.assertEquals(user, dto.user)
        Assert.assertEquals(attribution, dto.attribution)
        Assert.assertNull(dto.retargeting)
    }
}
