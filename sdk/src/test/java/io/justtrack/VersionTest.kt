package io.justtrack

import io.justtrack.versions.ApplicationVersionImpl
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
}
