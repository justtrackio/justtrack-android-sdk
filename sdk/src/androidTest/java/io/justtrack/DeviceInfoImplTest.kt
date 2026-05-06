package io.justtrack

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class DeviceInfoImplTest {

    private lateinit var realContext: Context

    @Before
    fun setUp() {
        // Use the real context from the instrumentation registry for most tests
        realContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun getAppVersion_returnsValidAppVersion() {
        val deviceInfo = DeviceInfoImpl(realContext)
        val appVersion = deviceInfo.getAppVersion()
        val packageInfo = realContext.packageManager.getPackageInfo(realContext.packageName, 0)

        assertNotNull("ApplicationVersion should not be null", appVersion)
        assertEquals("Version name should match", packageInfo.versionName ?: "", appVersion.getVersionName())
        assertTrue("Version code string should not be empty", appVersion.getVersionCode().isNotEmpty())
    }

    @Test
    fun getAppName_returnsValidAppName() {
        val deviceInfo = DeviceInfoImpl(realContext)
        val appName = deviceInfo.getAppName()
        val expectedAppName = realContext.applicationInfo.loadLabel(realContext.packageManager).toString()

        assertNotNull("App name should not be null", appName)
        assertEquals("App name should match the expected label", expectedAppName, appName)
    }

    @Test
    fun getAndroidId_returnsNonEmptyString() {
        val deviceInfo = DeviceInfoImpl(realContext)
        val androidId = deviceInfo.getAndroidId()
        assertNotNull("Android ID should not be null", androidId)
        assertTrue("Android ID should not be empty", androidId!!.isNotEmpty())
        assertNotEquals("Android ID should not be the invalid '9774d56d682e549c'", "9774d56d682e549c", androidId)
    }

    @Test
    fun getAndroidIdOrDefault_returnsIdWhenAvailable() {
        val deviceInfo = DeviceInfoImpl(realContext)
        val androidId = Settings.Secure.getString(realContext.contentResolver, Settings.Secure.ANDROID_ID)
        val result = deviceInfo.getAndroidIdOrDefault("default-id")
        assertEquals("Should return the actual Android ID", androidId, result)
    }

    @Test
    fun getCountryIso_returnsCorrectlyMappedIso() {
        val mockContext = mock(Context::class.java)
        val mockTelephonyManager = mock(TelephonyManager::class.java)
        val deviceInfo = DeviceInfoImpl(mockContext)
        `when`(mockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(mockTelephonyManager)

        // Standard ISO, should be uppercased
        `when`(mockTelephonyManager.networkCountryIso).thenReturn("de")
        assertEquals("DE", deviceInfo.getCountryIso())

        // Mapped ISO
        `when`(mockTelephonyManager.networkCountryIso).thenReturn("uk")
        assertEquals("GB", deviceInfo.getCountryIso())

        // ISO mapped to null
        `when`(mockTelephonyManager.networkCountryIso).thenReturn("eu")
        assertNull(deviceInfo.getCountryIso())

        // Null or Empty ISO
        `when`(mockTelephonyManager.networkCountryIso).thenReturn(null)
        assertNull(deviceInfo.getCountryIso())
        `when`(mockTelephonyManager.networkCountryIso).thenReturn("")
        assertNull(deviceInfo.getCountryIso())
    }

    @Test
    fun getDeviceLocale_returnsPrimaryLanguage() {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            realContext.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            realContext.resources.configuration.locale
        }

        val expectedLanguage = locale.language.lowercase()
        val deviceInfo = DeviceInfoImpl(realContext)

        assertEquals(expectedLanguage, deviceInfo.getDeviceLocale())
    }

    // --- ConnectionType Tests ---

    @Test
    fun getConnectionType_whenOffline_returnsOffline() {
        val mockContext = mock(Context::class.java)
        val mockConnectivityManager = mock(ConnectivityManager::class.java)
        val mockNetworkInfo = mock(NetworkInfo::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        `when`(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager)
        `when`(mockConnectivityManager.activeNetworkInfo).thenReturn(mockNetworkInfo)
        `when`(mockNetworkInfo.isConnected).thenReturn(false)
        val deviceInfo = DeviceInfoImpl(mockContext)

        assertEquals(ConnectionType.OFFLINE, deviceInfo.getConnectionType())
    }

    @Test
    fun getConnectionType_withMobile4G_returnsCellular4G() {
        val mockContext = mock(Context::class.java)
        val mockConnectivityManager = mock(ConnectivityManager::class.java)
        val mockNetworkInfo = mock(NetworkInfo::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        `when`(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(mockConnectivityManager)
        `when`(mockConnectivityManager.activeNetworkInfo).thenReturn(mockNetworkInfo)
        `when`(mockNetworkInfo.isConnected).thenReturn(true)
        `when`(mockNetworkInfo.type).thenReturn(ConnectivityManager.TYPE_MOBILE)
        `when`(mockNetworkInfo.subtype).thenReturn(TelephonyManager.NETWORK_TYPE_LTE)
        val deviceInfo = DeviceInfoImpl(mockContext)

        assertEquals(ConnectionType.CELLULAR_4G, deviceInfo.getConnectionType())
    }

    @Test
    fun getDeviceType_returnsCorrectTypeBasedOnResources() {
        // This test's outcome depends on the device/emulator it's running on.
        // It checks the value from R.bool.isTablet and asserts accordingly.
        val configuration = realContext.resources.configuration
        val isTablet = try {
            configuration.smallestScreenWidthDp >= 600
        } catch (e: Resources.NotFoundException) {
            // Fallback if the resource doesn't exist in the test app
            (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        }

        val expectedDeviceType = if (isTablet) DeviceType.TABLET else DeviceType.PHONE
        val deviceInfo = DeviceInfoImpl(realContext)
        assertEquals(expectedDeviceType, deviceInfo.getDeviceType())
    }

    @Test
    fun getDeviceName_returnsNonEmptyString() {
        val deviceInfo = DeviceInfoImpl(realContext)
        assertTrue(deviceInfo.deviceName.isNotEmpty())
    }

    @Test
    fun getDeviceModel_returnsNonEmptyString() {
        val deviceInfo = DeviceInfoImpl(realContext)
        assertTrue(deviceInfo.deviceModel.isNotEmpty())
    }

    @Test
    fun getDeviceProduct_returnsNonEmptyString() {
        val deviceInfo = DeviceInfoImpl(realContext)
        assertTrue(deviceInfo.deviceProduct.isNotEmpty())
    }

    @Test
    fun getOsVersion_returnsNonEmptyString() {
        val deviceInfo = DeviceInfoImpl(realContext)
        assertTrue(deviceInfo.osVersion.isNotEmpty())
    }

    @Test
    fun getOsLevel_returnsValidSdkInt() {
        val deviceInfo = DeviceInfoImpl(realContext)
        assertEquals(Build.VERSION.SDK_INT, deviceInfo.osLevel)
    }

    @Test
    fun getBuild_returnsNonEmptyString() {
        val deviceInfo = DeviceInfoImpl(realContext)
        assertTrue(deviceInfo.build.isNotEmpty())
    }

    @Test
    fun getDisplaySize_returnsNonZeroDimensions() {
        val deviceInfo = DeviceInfoImpl(realContext)
        val displaySize = deviceInfo.getDisplaySize()
        assertTrue("Display width should be greater than 0", displaySize.x > 0)
        assertTrue("Display height should be greater than 0", displaySize.y > 0)
    }

    @Test
    fun getCpuArch_returnsNonEmptyString() {
        val deviceInfo = DeviceInfoImpl(realContext)
        val cpuArch = deviceInfo.cpuArch
        assertNotNull("CPU Arch should not be null", cpuArch)
        assertTrue("CPU Arch should not be empty", cpuArch!!.isNotEmpty())
    }
}
