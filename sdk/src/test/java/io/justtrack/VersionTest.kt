package io.justtrack

import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersionImpl
import org.junit.Assert
import org.junit.Test

class VersionTest {

    @Test
    fun test_create_app_version() {
        val longVersionNumber = 10560L
        val versionName = "versionName"

        val version = ApplicationVersionImpl(name = versionName, code = longVersionNumber.toString())

        Assert.assertEquals(versionName, version.getVersionName())
        Assert.assertEquals(longVersionNumber, version.getVersionCode().toLong())
    }

    @Test
    fun test_create_app_version_empty_field() {
        val longVersionNumber = 123456L
        val name = "1.234.56"
        val versionEmptyName = ApplicationVersionImpl(name = null, code = longVersionNumber.toString())

        Assert.assertTrue(versionEmptyName.getVersionName().isEmpty())
        Assert.assertEquals(longVersionNumber, versionEmptyName.getVersionCode().toLong())

        val versionEmptyCode = ApplicationVersionImpl(name = name, code = null)

        Assert.assertEquals(name, versionEmptyCode.getVersionName())
        Assert.assertTrue(versionEmptyCode.getVersionCode().isEmpty())

        val versionEmpty = ApplicationVersionImpl(null, null)

        Assert.assertTrue(versionEmpty.getVersionCode().isEmpty())
        Assert.assertTrue(versionEmpty.getVersionCode().isEmpty())
    }

    @Test
    fun test_application_version_data_class_methods() {
        val version = ApplicationVersionImpl("1.0", "10")
        val same = version.copy()

        Assert.assertEquals(version, same)
        Assert.assertEquals(version.hashCode(), same.hashCode())
        Assert.assertNotEquals(version, version.copy(name = "2.0"))
        Assert.assertTrue(version.toString().contains("1.0"))
    }

    @Test
    fun test_sdk_version_defaults_and_data_class_methods() {
        val defaultName = SdkVersionImpl(major = 1, minor = 2, patch = 3, platformType = PlatformType.UNITY)
        val androidVersion = SdkVersionImpl(1, 2, 3, "custom")
        val same = defaultName.copy()

        Assert.assertEquals(1, defaultName.major)
        Assert.assertEquals(2, defaultName.minor)
        Assert.assertEquals(3, defaultName.patch)
        Assert.assertEquals("1.2.3", defaultName.name)
        Assert.assertEquals(PlatformType.UNITY, defaultName.platformType)
        Assert.assertEquals(PlatformType.ANDROID, androidVersion.platformType)
        Assert.assertEquals(defaultName, same)
        Assert.assertEquals(defaultName.hashCode(), same.hashCode())
        Assert.assertNotEquals(defaultName, defaultName.copy(minor = 4))
        Assert.assertTrue(defaultName.toString().contains("1.2.3"))
    }
}
