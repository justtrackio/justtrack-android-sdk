package io.justtrack.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigQueryParamsTest {
    @Test
    fun toMap_includesMandatoryFields() {
        val params = RemoteConfigQueryParams(
            installInstanceId = "install-1",
            osVersion = "14",
            appVersionCode = "123",
            appVersionName = "1.2.3",
            sdkVersionMajor = "1",
            sdkVersionMinor = "2",
            sdkVersionPatch = "3",
            sdkVersionName = "1.2.3",
            sdkVersionPlatform = "android",
            sdkVersionWrapper = null,
            deviceType = "PHONE",
            deviceModel = "Pixel",
            countryIso2 = null,
            deviceTimestamp = 1000000L,
            attributionTimestamp = null,
            firstSdkInitTimestamp = null,
            installTimestamp = 900000L,
        )

        val map = params.toMap()

        assertEquals("install-1", map["installInstanceId"])
        assertEquals("14", map["osVersion"])
        assertEquals("123", map["appVersionCode"])
        assertEquals("1.2.3", map["appVersionName"])
        assertEquals("1", map["sdkVersionMajor"])
        assertEquals("2", map["sdkVersionMinor"])
        assertEquals("3", map["sdkVersionPatch"])
        assertEquals("1.2.3", map["sdkVersionName"])
        assertEquals("android", map["sdkVersionPlatform"])
        assertEquals("PHONE", map["deviceType"])
        assertEquals("Pixel", map["deviceModel"])
        assertEquals("1000000", map["deviceTimestamp"])
        assertEquals("900000", map["installTimestamp"])
        assertFalse(map.containsKey("sdkVersionWrapper"))
        assertFalse(map.containsKey("countryIso2"))
        assertFalse(map.containsKey("attributionTimestamp"))
        assertFalse(map.containsKey("firstSdkInitTimestamp"))
    }

    @Test
    fun toMap_includesOptionalFieldsWhenPresent() {
        val params = RemoteConfigQueryParams(
            installInstanceId = "install-2",
            osVersion = "13",
            appVersionCode = "321",
            appVersionName = "9.9.9",
            sdkVersionMajor = "9",
            sdkVersionMinor = "8",
            sdkVersionPatch = "7",
            sdkVersionName = "9.8.7",
            sdkVersionPlatform = "android",
            sdkVersionWrapper = "react",
            deviceType = "TABLET",
            deviceModel = "Nexus",
            countryIso2 = "DE",
            deviceTimestamp = 2000000L,
            attributionTimestamp = 1500000L,
            firstSdkInitTimestamp = 1200000L,
            installTimestamp = 1100000L,
        )

        val map = params.toMap()

        assertEquals("react", map["sdkVersionWrapper"])
        assertEquals("DE", map["countryIso2"])
        assertEquals("2000000", map["deviceTimestamp"])
        assertEquals("1500000", map["attributionTimestamp"])
        assertEquals("1200000", map["firstSdkInitTimestamp"])
        assertEquals("1100000", map["installTimestamp"])
        assertTrue(map.containsKey("sdkVersionWrapper"))
        assertTrue(map.containsKey("countryIso2"))
        assertTrue(map.containsKey("attributionTimestamp"))
        assertTrue(map.containsKey("firstSdkInitTimestamp"))
    }
}
