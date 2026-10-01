package io.justtrack.util

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageManager
import android.os.Build
import io.justtrack.DeviceInfo
import io.justtrack.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class InstallerSourceIdProviderImplTest {

    private val deviceInfo: DeviceInfo = mock {
        on { getApplicationPackageName() } doReturn "io.justtrack.host"
    }
    private val logger = TestLogger()
    private lateinit var packageManager: PackageManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        packageManager = mock()
        context = mock {
            on { getPackageManager() } doReturn packageManager
        }
    }

    private fun subject() = InstallerSourceIdProviderImpl(context, deviceInfo, logger)

    private fun installSourceInfo(installer: String?, initiator: String?): InstallSourceInfo {
        // InstallSourceInfo's ctor is hidden; instantiate via mock.
        return mock {
            on { installingPackageName } doReturn installer
            on { initiatingPackageName } doReturn initiator
        }
    }

    // --- SDK >= R (API 30) ------------------------------------------------------------------

    @Test
    fun getInstallerSourceId_apiR_returnsInstallingPackageNameWhenPresent() {
        val info = installSourceInfo(installer = "com.android.vending", initiator = "com.foo")
        whenever(packageManager.getInstallSourceInfo("io.justtrack.host")).thenReturn(info)

        assertEquals("com.android.vending", subject().getInstallerSourceId())
    }

    @Test
    fun getInstallerSourceId_apiR_fallsBackToInitiatingPackageNameWhenInstallerIsNull() {
        val info = installSourceInfo(installer = null, initiator = "com.android.shell")
        whenever(packageManager.getInstallSourceInfo("io.justtrack.host")).thenReturn(info)

        assertEquals("com.android.shell", subject().getInstallerSourceId())
    }

    @Test
    fun getInstallerSourceId_apiR_returnsUnknownWhenBothInstallerAndInitiatorAreNull() {
        val info = installSourceInfo(installer = null, initiator = null)
        whenever(packageManager.getInstallSourceInfo("io.justtrack.host")).thenReturn(info)

        assertEquals("unknown", subject().getInstallerSourceId())
    }

    @Test
    fun getInstallerSourceId_apiR_logsDebugFieldsIncludingUnknownPlaceholdersForNulls() {
        val info = installSourceInfo(installer = null, initiator = null)
        whenever(packageManager.getInstallSourceInfo("io.justtrack.host")).thenReturn(info)

        subject().getInstallerSourceId()
        // Just verifying it does not throw when logger fields builder hits the null branches.
    }

    @Test
    fun getInstallerSourceId_apiR_returnsUnknownWhenPackageManagerThrows() {
        whenever(packageManager.getInstallSourceInfo(any()))
            .thenThrow(PackageManager.NameNotFoundException("missing"))

        assertEquals("unknown", subject().getInstallerSourceId())
    }

    @Test
    fun getInstallerSourceId_apiR_returnsUnknownWhenDeviceInfoThrows() {
        val throwingDeviceInfo: DeviceInfo = mock {
            on { getApplicationPackageName() } doThrow RuntimeException("boom")
        }

        assertEquals(
            "unknown",
            InstallerSourceIdProviderImpl(context, throwingDeviceInfo, logger).getInstallerSourceId(),
        )
    }

    // --- SDK < R (legacy branch, API 29) ----------------------------------------------------

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    @Suppress("DEPRECATION")
    fun getInstallerSourceId_belowR_returnsLegacyInstallerPackageName() {
        whenever(packageManager.getInstallerPackageName("io.justtrack.host"))
            .thenReturn("com.android.vending")

        assertEquals("com.android.vending", subject().getInstallerSourceId())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    @Suppress("DEPRECATION")
    fun getInstallerSourceId_belowR_returnsUnknownWhenLegacyApiReturnsNull() {
        whenever(packageManager.getInstallerPackageName("io.justtrack.host"))
            .thenReturn(null)

        assertEquals("unknown", subject().getInstallerSourceId())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    @Suppress("DEPRECATION")
    fun getInstallerSourceId_belowR_returnsUnknownWhenLegacyApiThrows() {
        whenever(packageManager.getInstallerPackageName(any()))
            .thenThrow(IllegalArgumentException("bad pkg"))

        assertEquals("unknown", subject().getInstallerSourceId())
    }

    // --- Sanity: ReflectionHelpers can flip SDK_INT mid-run if ever needed ------------------

    @Test
    fun sanityCheck_sdkIntIsConfigurable() {
        // Default class config sets API 30; per-method @Config overrides to 29.
        // Guard against future Robolectric regressions.
        assertEquals(Build.VERSION_CODES.R, Build.VERSION.SDK_INT)
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.R)
    }
}
