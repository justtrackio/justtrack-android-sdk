package io.justtrack

import io.justtrack.installreferrer.api.ReferrerDetails
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersionImpl
import io.justtrack.versions.VersionBundle
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import java.util.UUID

internal class AttributionDTOTest : BaseDTOTest() {
    @Test
    @Throws(JSONException::class)
    fun serializeInput() {
        val details = Mockito.mock(ReferrerDetails::class.java)
        Mockito.`when`(details.installReferrer).thenReturn("my install referrer")
        Mockito.`when`(details.referrerClickTimestampSeconds).thenReturn(441903785L)
        Mockito.`when`(details.installBeginTimestampSeconds).thenReturn(441904785L)
        val claims: MutableList<String> = ArrayList()
        claims.add("claim 1")
        claims.add("claim 2")
        val builder = AttributionInputBuilder(
            createVersionBundle(),
            deviceInfoImpl,
            "advertiser id",
            false,
            "my tracking id",
            "my tracking provider",
            claims,
            "play store",
            details,
            "app set id",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
        )
        val input = builder.build()
        val json = input.toJSON(Formatter)
        validateCommon(builder, json)
        validateReferrer(builder, json.getJSONObject("referrer"), Formatter)
    }

    @Test
    @Throws(JSONException::class)
    fun serializeInputNoReferrer() {
        val claims: MutableList<String> = ArrayList()
        claims.add("claim 1")
        claims.add("claim 2")
        val builder = AttributionInputBuilder(
            createVersionBundle(),
            deviceInfoImpl,
            "user_id",
            false,
            null,
            "advertiserId",
            claims,
            "play store",
            null,
            "app set id",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
        )
        val input = builder.build()
        val json = input.toJSON(Formatter)
        validateCommon(builder, json)
        Assert.assertEquals(JSONObject.NULL, json["referrer"])
    }

    @Throws(JSONException::class)
    private fun validateCommon(builder: AttributionInputBuilder, json: JSONObject) {
        val appVersion = json.getJSONObject("appVersion")
        val sdkVersion = json.getJSONObject("sdkVersion")
        Assert.assertEquals(builder.appVersion.getVersionName(), appVersion.getString("name"))
        Assert.assertEquals(builder.appVersion.getVersionCode(), appVersion.getString("code"))
        Assert.assertEquals(builder.sdkVersion.major.toLong(), sdkVersion.getInt("major").toLong())
        Assert.assertEquals(builder.sdkVersion.minor.toLong(), sdkVersion.getInt("minor").toLong())
        Assert.assertEquals(builder.sdkVersion.patch.toLong(), sdkVersion.getInt("patch").toLong())
        Assert.assertEquals(builder.sdkVersion.name, sdkVersion.getString("name"))

        val user = json.getJSONObject("user")
        Assert.assertEquals(builder.deviceId, user.getString("deviceId"))
        Assert.assertEquals(builder.advertiserId, user.getString("advertiserId"))
        Assert.assertEquals(builder.hasLimitedAdTracking, user.getBoolean("hasLimitedAdTracking"))
        Assert.assertEquals(builder.trackingId, if (user.isNull("trackingId")) null else user.getString("trackingId"))
        Assert.assertEquals(builder.trackingProvider, user.getString("trackingProvider"))
        Assert.assertEquals(builder.countryIso, user.getString("countryIso"))

        val device = json.getJSONObject("device")
        Assert.assertEquals(builder.deviceName, device.getString("name"))
        Assert.assertEquals(builder.deviceModel, device.getString("model"))
        Assert.assertEquals(builder.deviceProduct, device.getString("product"))
        Assert.assertEquals(if (builder.deviceType == DeviceType.PHONE) "phone" else "tablet", device.getString("type"))

        val os = device.getJSONObject("os")
        Assert.assertEquals(builder.osVersion, os.getString("version"))
        Assert.assertEquals(builder.osName, os.getString("name"))

        val display = device.getJSONObject("display")
        Assert.assertEquals(builder.displayWidth.toLong(), display.getInt("width").toLong())
        Assert.assertEquals(builder.displayHeight.toLong(), display.getInt("height").toLong())

        val claims = json.getJSONArray("claims")
        Assert.assertEquals(2, claims.length().toLong())
        Assert.assertEquals("claim 1", claims.getString(0))
        Assert.assertEquals("claim 2", claims.getString(1))

        val parameters = json.getJSONObject("parameters")
        Assert.assertEquals("play store", parameters.getString("installSource"))
    }

    @Throws(JSONException::class)
    private fun validateReferrer(builder: AttributionInputBuilder, json: JSONObject, formatter: Formatter) {
        val details = builder.referrerDetails
        Assert.assertNotNull(details)
        Assert.assertEquals(details!!.installReferrer, json.getString("value"))
        Assert.assertEquals("1984-01-02T15:03:05.000Z", json.getString("clickDate"))
        Assert.assertEquals("1984-01-02T15:19:45.000Z", json.getString("installBeginDate"))
        Assert.assertEquals(formatter.formatDateMilliseconds(builder.clientDate), json.getString("clientDate"))
    }

    private fun createVersionBundle(): VersionBundle {
        return VersionBundle(
            SdkVersionImpl(1, 0, 0, "1.0.0", PlatformType.ANDROID),
            ApplicationVersionImpl("1.0.0", "100"),
        )
    }
}
