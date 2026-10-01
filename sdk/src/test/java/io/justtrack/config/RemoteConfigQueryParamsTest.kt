package io.justtrack.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun equals_returnsTrueForStructurallyEqualInstances() {
        val a = sample()
        val b = sample()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equals_returnsFalseWhenFieldsDiffer() {
        assertNotEquals(sample(), sample().copy(countryIso2 = "US"))
    }

    @Test
    fun copy_overridesSelectedFieldsOnly() {
        val original = sample()
        val copy = original.copy(countryIso2 = "US", installTimestamp = 999L)

        assertEquals("US", copy.countryIso2)
        assertEquals(999L, copy.installTimestamp)
        assertEquals(original.installInstanceId, copy.installInstanceId)
        assertEquals(original.osVersion, copy.osVersion)
        assertNotEquals(original, copy)
    }

    @Test
    fun componentN_returnsConstructorFieldsInOrder() {
        val p = sample()

        assertEquals("iid", p.component1())
        assertEquals("14", p.component2())
        assertEquals("100", p.component3())
        assertEquals("1.0.0", p.component4())
        assertEquals("1", p.component5())
        assertEquals("2", p.component6())
        assertEquals("3", p.component7())
        assertEquals("1.2.3", p.component8())
        assertEquals("android", p.component9())
        assertEquals("wrapper", p.component10())
        assertEquals("PHONE", p.component11())
        assertEquals("Pixel", p.component12())
        assertEquals("DE", p.component13())
        assertEquals(50L, p.component14())
        assertEquals(100L, p.component15())
        assertEquals(200L, p.component16())
        assertEquals(300L, p.component17())
    }

    @Test
    fun toString_containsKeyFieldNames() {
        val text = sample().toString()

        assertTrue("missing installInstanceId: $text", text.contains("installInstanceId"))
        assertTrue("missing countryIso2: $text", text.contains("countryIso2"))
        assertTrue("missing installTimestamp: $text", text.contains("installTimestamp"))
    }

    @Test
    fun propertyGetters_returnConstructorValues() {
        val p = sample()

        assertEquals("iid", p.installInstanceId)
        assertEquals("14", p.osVersion)
        assertEquals("100", p.appVersionCode)
        assertEquals("1.0.0", p.appVersionName)
        assertEquals("1", p.sdkVersionMajor)
        assertEquals("2", p.sdkVersionMinor)
        assertEquals("3", p.sdkVersionPatch)
        assertEquals("1.2.3", p.sdkVersionName)
        assertEquals("android", p.sdkVersionPlatform)
        assertEquals("wrapper", p.sdkVersionWrapper)
        assertEquals("PHONE", p.deviceType)
        assertEquals("Pixel", p.deviceModel)
        assertEquals("DE", p.countryIso2)
        assertEquals(50L, p.deviceTimestamp)
        assertEquals(100L, p.attributionTimestamp)
        assertEquals(200L, p.firstSdkInitTimestamp)
        assertEquals(300L, p.installTimestamp)
    }

    private fun sample() = RemoteConfigQueryParams(
        installInstanceId = "iid",
        osVersion = "14",
        appVersionCode = "100",
        appVersionName = "1.0.0",
        sdkVersionMajor = "1",
        sdkVersionMinor = "2",
        sdkVersionPatch = "3",
        sdkVersionName = "1.2.3",
        sdkVersionPlatform = "android",
        sdkVersionWrapper = "wrapper",
        deviceType = "PHONE",
        deviceModel = "Pixel",
        countryIso2 = "DE",
        deviceTimestamp = 50L,
        attributionTimestamp = 100L,
        firstSdkInitTimestamp = 200L,
        installTimestamp = 300L,
    )
}
