package io.justtrack.dtos

import io.justtrack.DeviceType
import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DTOAttributionInputTest {
    private fun makeAppVersion() = DTOAppVersion(name = "1.0.0", code = "100")
    private fun makeSdkVersion() = DTOSdkVersion(
        major = 1,
        minor = 0,
        patch = 0,
        name = "1.0.0",
        platform = "android",
        wrapper = null,
    )
    private fun makeUser() = DTOAttributionInputUser(
        userId = "user-id",
        customUserId = null,
        installInstanceId = "install-id",
        deviceId = "device-id",
        advertiserId = "advertiser-id",
        trackingId = "tracking-id",
        trackingProvider = "provider",
        countryIso = "US",
        appSetId = "appset-id",
        hasLimitedAdTracking = false,
    )
    private fun makeDevice() = DTOAttributionInputDevice(
        name = "Pixel",
        model = "Pixel",
        product = "pixel",
        type = DeviceType.PHONE,
        os = DTOAttributionInputDeviceOS(version = "13", name = "Android"),
        display = DTOAttributionInputDeviceDisplay(width = 1080, height = 1920),
    )
    private fun makeParameters() = DTOAttributionInputParameters(
        installSource = "play_store",
        integritySecret = null,
    )
    private fun makeReferrer() = DTOAttributionInputReferrer(
        value = "utm_source=test",
        clickDate = Date(1_592_228_819_000L),
        installBeginDate = Date(1_592_228_820_000L),
        clientDate = Date(1_592_228_821_000L),
        serverClickDate = Date(1_592_228_822_000L),
        serverInstallBeginDate = Date(1_592_228_823_000L),
        installVersion = null,
    )

    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val appVersion = makeAppVersion()
        val sdkVersion = makeSdkVersion()
        val user = makeUser()
        val device = makeDevice()
        val claims = listOf("claim1", "claim2")
        val parameters = makeParameters()
        val referrer = makeReferrer()

        val dto = DTOAttributionInput(
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            user = user,
            device = device,
            claims = claims,
            parameters = parameters,
            referrer = referrer,
        )

        Assert.assertEquals(appVersion, dto.appVersion)
        Assert.assertEquals(sdkVersion, dto.sdkVersion)
        Assert.assertEquals(user, dto.user)
        Assert.assertEquals(device, dto.device)
        Assert.assertEquals(claims, dto.claims)
        Assert.assertEquals(parameters, dto.parameters)
        Assert.assertEquals(referrer, dto.referrer)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOAttributionInput(
            appVersion = makeAppVersion(),
            sdkVersion = makeSdkVersion(),
            user = makeUser(),
            device = makeDevice(),
            claims = listOf("c1", "c2"),
            parameters = makeParameters(),
            referrer = makeReferrer(),
        )

        val json = dto.toJSON(Formatter)

        val appVersionJson = json.getJSONObject("appVersion")
        Assert.assertEquals("1.0.0", appVersionJson.getString("name"))
        Assert.assertEquals("100", appVersionJson.getString("code"))

        val sdkVersionJson = json.getJSONObject("sdkVersion")
        Assert.assertEquals(1, sdkVersionJson.getInt("major"))

        val userJson = json.getJSONObject("user")
        Assert.assertEquals("device-id", userJson.getString("deviceId"))

        val deviceJson = json.getJSONObject("device")
        Assert.assertEquals("Pixel", deviceJson.getString("name"))

        val claimsArr = json.getJSONArray("claims")
        Assert.assertEquals(2, claimsArr.length())
        Assert.assertEquals("c1", claimsArr.getString(0))
        Assert.assertEquals("c2", claimsArr.getString(1))

        val paramsJson = json.getJSONObject("parameters")
        Assert.assertEquals("play_store", paramsJson.getString("installSource"))

        // referrer present
        Assert.assertFalse(json.isNull("referrer"))
        Assert.assertEquals("utm_source=test", json.getJSONObject("referrer").getString("value"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSetsReferrerToNullWhenAbsent() {
        val dto = DTOAttributionInput(
            appVersion = makeAppVersion(),
            sdkVersion = makeSdkVersion(),
            user = makeUser(),
            device = makeDevice(),
            claims = emptyList(),
            parameters = makeParameters(),
            referrer = null,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(JSONObject.NULL, json["referrer"])
    }
}
