package io.justtrack

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Display
import android.view.WindowManager
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
internal class DeviceInfoImplUnitTest {
    private lateinit var context: Context
    private lateinit var deviceInfo: DeviceInfoImpl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        deviceInfo = DeviceInfoImpl(context)
    }

    @After
    fun tearDown() {
        ReflectionHelpers.setStaticField(JustTrack::class.java, "activityLifecycleListener", null)
    }

    @Test
    fun getAppVersion_returnsPackageVersion() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val appVersion = deviceInfo.getAppVersion()

        Assert.assertEquals(packageInfo.versionName ?: "", appVersion.getVersionName())
        Assert.assertEquals(packageInfo.longVersionCode.toString(), appVersion.getVersionCode())
    }

    @Test
    fun getAppVersion_whenPackageInfoMissing_returnsEmptyVersion() {
        val context: Context = mock()
        val packageManager: PackageManager = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.packageName).thenReturn("missing.package")
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(packageManager.getPackageInfo("missing.package", 0)).thenThrow(PackageManager.NameNotFoundException())

        val appVersion = deviceInfo.getAppVersion()

        Assert.assertEquals("", appVersion.getVersionName())
        Assert.assertEquals("", appVersion.getVersionCode())
    }

    @Test
    @Config(sdk = [27])
    fun getAppVersion_beforeApi28_usesDeprecatedVersionCode() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        val appVersion = deviceInfo.getAppVersion()

        @Suppress("DEPRECATION")
        Assert.assertEquals(packageInfo.versionCode.toString(), appVersion.getVersionCode())
    }

    @Test
    fun getAppVersion_withLongVersionCodeMajorAndMinor_formatsBothParts() {
        val context: Context = mock()
        val packageManager: PackageManager = mock()
        val packageInfo = PackageInfo().apply {
            versionName = "1.2.3"
            setLongVersionCode((2L shl 32) or 42L)
        }
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.packageName).thenReturn("test.package")
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(packageManager.getPackageInfo("test.package", 0)).thenReturn(packageInfo)

        val appVersion = deviceInfo.getAppVersion()

        Assert.assertEquals("1.2.3", appVersion.getVersionName())
        Assert.assertEquals("2.42", appVersion.getVersionCode())
    }

    @Test
    fun getApplicationPackageName_returnsContextPackageName() {
        Assert.assertEquals(context.packageName, deviceInfo.getApplicationPackageName())
    }

    @Test
    fun getAppName_returnsApplicationLabel() {
        val applicationInfo = context.applicationInfo
        val expectedAppName = applicationInfo.nonLocalizedLabel?.toString()
            ?: applicationInfo.labelRes.takeIf { it != 0 }?.let { context.getString(it) }

        Assert.assertEquals(expectedAppName, deviceInfo.getAppName())
    }

    @Test
    fun getAppName_prefersNonLocalizedLabel() {
        val context: Context = mock()
        val applicationInfo = ApplicationInfo().apply {
            nonLocalizedLabel = "Non localized label"
            labelRes = 1
        }
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationInfo).thenReturn(applicationInfo)

        Assert.assertEquals("Non localized label", deviceInfo.getAppName())
    }

    @Test
    fun getAppName_usesStringResourceWhenNonLocalizedLabelMissing() {
        val context: Context = mock()
        val applicationInfo = ApplicationInfo().apply {
            labelRes = 1
        }
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationInfo).thenReturn(applicationInfo)
        whenever(context.getString(1)).thenReturn("Resource label")

        Assert.assertEquals("Resource label", deviceInfo.getAppName())
    }

    @Test
    fun getAppName_returnsNullWhenNoLabelIsAvailable() {
        val context: Context = mock()
        val applicationInfo = ApplicationInfo()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationInfo).thenReturn(applicationInfo)

        Assert.assertNull(deviceInfo.getAppName())
    }

    @Test
    fun getAppName_returnsNullWhenApplicationInfoMissing() {
        val context: Context = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationInfo).thenReturn(null)

        Assert.assertNull(deviceInfo.getAppName())
    }

    @Test
    fun getAndroidIdOrDefault_returnsAndroidIdWhenPresent() {
        val androidId = "android-id"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, androidId)

        Assert.assertEquals(androidId, deviceInfo.getAndroidIdOrDefault("default-id"))
    }

    @Test
    fun getAndroidIdOrDefault_returnsDefaultWhenAndroidIdMissing() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, null)

        Assert.assertEquals("default-id", deviceInfo.getAndroidIdOrDefault("default-id"))
    }

    @Test
    fun getCountryIso_uppercasesAndMapsReservedCodes() {
        val context: Context = mock()
        val telephonyManager: TelephonyManager = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager)

        whenever(telephonyManager.networkCountryIso).thenReturn("de")
        Assert.assertEquals("DE", deviceInfo.getCountryIso())

        whenever(telephonyManager.networkCountryIso).thenReturn("uk")
        Assert.assertEquals("GB", deviceInfo.getCountryIso())

        whenever(telephonyManager.networkCountryIso).thenReturn("eu")
        Assert.assertNull(deviceInfo.getCountryIso())

        whenever(telephonyManager.networkCountryIso).thenReturn("")
        Assert.assertNull(deviceInfo.getCountryIso())

        whenever(telephonyManager.networkCountryIso).thenReturn(null)
        Assert.assertNull(deviceInfo.getCountryIso())
    }

    @Test
    fun getDeviceLocale_returnsPrimaryLocaleLanguage() {
        val expectedLanguage = context.resources.configuration.locales[0].language.lowercase()

        Assert.assertEquals(expectedLanguage, deviceInfo.getDeviceLocale())
    }

    @Test
    fun getDeviceLocale_returnsNullWhenPrimaryLocaleHasNoLanguage() {
        val context = contextWithConfiguration(
            Configuration().apply {
                setLocales(LocaleList(Locale("")))
            },
        )
        val deviceInfo = DeviceInfoImpl(context)

        Assert.assertNull(deviceInfo.getDeviceLocale())
    }

    @Test
    fun getDeviceLocale_returnsNullWhenLocaleListIsEmpty() {
        val context = contextWithConfiguration(
            Configuration().apply {
                setLocales(LocaleList.getEmptyLocaleList())
            },
        )
        val deviceInfo = DeviceInfoImpl(context)

        Assert.assertNull(deviceInfo.getDeviceLocale())
    }

    @Test
    @Config(sdk = [23])
    fun getLocales_beforeApi24_usesConfigurationLocale() {
        val configuration = Configuration().apply {
            @Suppress("DEPRECATION")
            locale = Locale("DE")
        }
        val context = contextWithConfiguration(configuration)
        val deviceInfo = DeviceInfoImpl(context)

        Assert.assertEquals(listOf(Locale("DE")), deviceInfo.getLocales(context))
    }

    @Test
    fun getDeviceType_usesSmallestScreenWidth() {
        val expectedDeviceType = if (context.resources.configuration.smallestScreenWidthDp >= 600) {
            DeviceType.TABLET
        } else {
            DeviceType.PHONE
        }

        Assert.assertEquals(expectedDeviceType, deviceInfo.getDeviceType())
    }

    @Test
    fun getDeviceType_returnsTabletForLargeSmallestWidth() {
        val context = contextWithConfiguration(
            Configuration().apply {
                smallestScreenWidthDp = 600
            },
        )

        Assert.assertEquals(DeviceType.TABLET, DeviceInfoImpl(context).getDeviceType())
    }

    @Test
    fun getDeviceType_returnsPhoneForSmallestWidthBelowTabletThreshold() {
        val context = contextWithConfiguration(
            Configuration().apply {
                smallestScreenWidthDp = 599
            },
        )

        Assert.assertEquals(DeviceType.PHONE, DeviceInfoImpl(context).getDeviceType())
    }

    @Test
    fun getDisplaySize_returnsNonZeroDimensions() {
        val displaySize = deviceInfo.getDisplaySize()

        Assert.assertTrue(displaySize.x > 0)
        Assert.assertTrue(displaySize.y > 0)
    }

    @Test
    @Config(sdk = [30])
    fun getDisplaySize_api30UsesContextDisplayWhenAvailable() {
        val displaySize = deviceInfo.getDisplaySize()

        Assert.assertTrue(displaySize.x > 0)
        Assert.assertTrue(displaySize.y > 0)
    }

    @Test
    @Config(sdk = [30])
    fun getDisplaySize_api30ReturnsContextDisplaySize() {
        val context: Context = mock()
        val display: Display = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.display).thenReturn(display)

        val displaySize = deviceInfo.getDisplaySize()

        Assert.assertEquals(0, displaySize.x)
        Assert.assertEquals(0, displaySize.y)
        verify(display).getRealSize(displaySize)
    }

    @Test
    @Config(sdk = [30])
    fun getDisplaySize_whenContextDisplayThrows_fallsBackToWindowManager() {
        val context: Context = mock()
        val windowManager: WindowManager = mock()
        val display: Display = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.display).thenThrow(UnsupportedOperationException())
        whenever(context.getSystemService(Context.WINDOW_SERVICE)).thenReturn(windowManager)
        whenever(windowManager.defaultDisplay).thenReturn(display)

        val displaySize = deviceInfo.getDisplaySize()

        Assert.assertEquals(0, displaySize.x)
        Assert.assertEquals(0, displaySize.y)
    }

    @Test
    fun getDisplaySize_usesCurrentActivityContextWhenAvailable() {
        val application: Context = mock()
        val activity: Activity = mock()
        val activityWindowManager: WindowManager = mock()
        val display: Display = mock()
        val listener = ActivityLifecycleListener()
        listener.onActivityResumed(activity)
        ReflectionHelpers.setStaticField(JustTrack::class.java, "activityLifecycleListener", listener)
        val deviceInfo = DeviceInfoImpl(application)
        whenever(activity.getSystemService(Context.WINDOW_SERVICE)).thenReturn(activityWindowManager)
        whenever(activityWindowManager.defaultDisplay).thenReturn(display)

        val displaySize = deviceInfo.getDisplaySize()

        Assert.assertEquals(0, displaySize.x)
        Assert.assertEquals(0, displaySize.y)
        verify(activity).getSystemService(Context.WINDOW_SERVICE)
    }

    @Test
    fun getDisplaySize_withMissingDefaultDisplay_returnsZeroSize() {
        val context: Context = mock()
        val windowManager: WindowManager = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.getSystemService(Context.WINDOW_SERVICE)).thenReturn(windowManager)
        whenever(windowManager.defaultDisplay).thenReturn(null)

        Assert.assertEquals(Point(0, 0), deviceInfo.getDisplaySize())
    }

    @Test
    fun getStaticDeviceFields_returnBuildValues() {
        Assert.assertEquals(Build.DEVICE, deviceInfo.deviceName)
        Assert.assertEquals(Build.MODEL, deviceInfo.deviceModel)
        Assert.assertEquals(Build.PRODUCT, deviceInfo.deviceProduct)
        Assert.assertEquals(Build.VERSION.RELEASE, deviceInfo.osVersion)
        Assert.assertEquals("Android", deviceInfo.osName)
        Assert.assertEquals(Build.VERSION.SDK_INT, deviceInfo.osLevel)
        Assert.assertEquals(Build.ID, deviceInfo.build)
        Assert.assertEquals(System.getProperty("os.arch"), deviceInfo.cpuArch)
    }

    @Test
    fun cpuArch_fallsBackToSupportedAbiWhenSystemPropertyMissing() {
        val previousArch = System.getProperty("os.arch")
        try {
            System.clearProperty("os.arch")

            Assert.assertEquals(Build.SUPPORTED_ABIS.first(), deviceInfo.cpuArch)
        } finally {
            if (previousArch == null) {
                System.clearProperty("os.arch")
            } else {
                System.setProperty("os.arch", previousArch)
            }
        }
    }

    @Test
    fun cpuArch_returnsNullWhenSystemPropertyAndSupportedAbisAreMissing() {
        val previousArch = System.getProperty("os.arch")
        val previousSupportedAbis = Build.SUPPORTED_ABIS
        try {
            System.clearProperty("os.arch")
            ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", emptyArray<String>())

            Assert.assertNull(deviceInfo.cpuArch)
        } finally {
            ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", previousSupportedAbis)
            if (previousArch == null) {
                System.clearProperty("os.arch")
            } else {
                System.setProperty("os.arch", previousArch)
            }
        }
    }

    @Test
    fun getConnectionType_whenSystemServiceThrows_returnsUnknown() {
        val context: Context = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationContext).thenReturn(context)
        doThrow(RuntimeException("missing service")).whenever(context).getSystemService(Context.CONNECTIVITY_SERVICE)

        Assert.assertEquals(ConnectionType.UNKNOWN, deviceInfo.getConnectionType())
    }

    @Test
    fun getConnectionType_returnsApi23ConnectionTypeWhenKnown() {
        val context: Context = mock()
        val connectivityManager = connectivityManagerWithTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)

        Assert.assertEquals(ConnectionType.WIFI, deviceInfo.getConnectionType())
    }

    @Test
    fun getConnectionType_fallsBackToLegacyConnectionTypeWhenApi23IsUnknown() {
        val context: Context = mock()
        val connectivityManager: ConnectivityManager = mock()
        val networkInfo: NetworkInfo = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        whenever(connectivityManager.activeNetwork).thenReturn(null)
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnected).thenReturn(true)
        whenever(networkInfo.type).thenReturn(ConnectivityManager.TYPE_WIFI)

        Assert.assertEquals(ConnectionType.WIFI, deviceInfo.getConnectionType())
    }

    @Test
    @Config(sdk = [22])
    fun getConnectionType_beforeApi23_usesLegacyConnectionType() {
        val context: Context = mock()
        val connectivityManager: ConnectivityManager = mock()
        val networkInfo: NetworkInfo = mock()
        val deviceInfo = DeviceInfoImpl(context)
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnected).thenReturn(true)
        whenever(networkInfo.type).thenReturn(ConnectivityManager.TYPE_WIFI)

        Assert.assertEquals(ConnectionType.WIFI, deviceInfo.getConnectionType())
    }

    @Test
    fun getConnectionTypeApi23_returnsUnknownWithoutActiveNetwork() {
        val connectivityManager: ConnectivityManager = mock()
        whenever(connectivityManager.activeNetwork).thenReturn(null)

        Assert.assertEquals(ConnectionType.UNKNOWN, deviceInfo.getConnectionTypeApi23(connectivityManager))
    }

    @Test
    fun getConnectionTypeApi23_returnsUnknownWithoutNetworkCapabilities() {
        val connectivityManager: ConnectivityManager = mock()
        val network: Network = mock()
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(connectivityManager.getNetworkCapabilities(network)).thenReturn(null)

        Assert.assertEquals(ConnectionType.UNKNOWN, deviceInfo.getConnectionTypeApi23(connectivityManager))
    }

    @Test
    fun getConnectionTypeApi23_mapsKnownTransports() {
        Assert.assertEquals(ConnectionType.WIFI, connectionTypeForTransport(NetworkCapabilities.TRANSPORT_WIFI))
        Assert.assertEquals(ConnectionType.BLUETOOTH, connectionTypeForTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))
        Assert.assertEquals(ConnectionType.ETHERNET, connectionTypeForTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        Assert.assertEquals(ConnectionType.VPN, connectionTypeForTransport(NetworkCapabilities.TRANSPORT_VPN))
    }

    @Test
    fun getConnectionTypeApi23_forCellularFallsBackToLegacyNetworkInfo() {
        val connectivityManager = connectivityManagerWithTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val networkInfo: NetworkInfo = mock()
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnected).thenReturn(true)
        whenever(networkInfo.type).thenReturn(ConnectivityManager.TYPE_MOBILE)
        whenever(networkInfo.subtype).thenReturn(TelephonyManager.NETWORK_TYPE_HSPA)

        Assert.assertEquals(ConnectionType.CELLULAR_3G, deviceInfo.getConnectionTypeApi23(connectivityManager))
    }

    @Test
    fun getConnectionTypeApi23_returnsUnknownForUnhandledTransport() {
        val connectivityManager = connectivityManagerWithTransport(null)

        Assert.assertEquals(ConnectionType.UNKNOWN, deviceInfo.getConnectionTypeApi23(connectivityManager))
    }

    @Test
    fun getConnectionTypeApiAll_returnsUnknownWithoutNetworkInfo() {
        val connectivityManager: ConnectivityManager = mock()
        whenever(connectivityManager.activeNetworkInfo).thenReturn(null)

        Assert.assertEquals(ConnectionType.UNKNOWN, deviceInfo.getConnectionTypeApiAll(connectivityManager))
    }

    @Test
    fun getConnectionTypeApiAll_returnsOfflineWhenNetworkDisconnected() {
        val connectivityManager: ConnectivityManager = mock()
        val networkInfo: NetworkInfo = mock()
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnected).thenReturn(false)

        Assert.assertEquals(ConnectionType.OFFLINE, deviceInfo.getConnectionTypeApiAll(connectivityManager))
    }

    @Test
    fun getConnectionTypeApiAll_returnsWifiForWifiNetwork() {
        val connectivityManager: ConnectivityManager = mock()
        val networkInfo: NetworkInfo = mock()
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnected).thenReturn(true)
        whenever(networkInfo.type).thenReturn(ConnectivityManager.TYPE_WIFI)

        Assert.assertEquals(ConnectionType.WIFI, deviceInfo.getConnectionTypeApiAll(connectivityManager))
    }

    @Test
    fun getConnectionTypeApiAll_returnsMobileConnectionType() {
        val connectivityManager: ConnectivityManager = mock()
        val networkInfo: NetworkInfo = mock()
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnected).thenReturn(true)
        whenever(networkInfo.type).thenReturn(ConnectivityManager.TYPE_MOBILE)
        whenever(networkInfo.subtype).thenReturn(TelephonyManager.NETWORK_TYPE_LTE)

        Assert.assertEquals(ConnectionType.CELLULAR_4G, deviceInfo.getConnectionTypeApiAll(connectivityManager))
    }

    @Test
    fun getConnectionTypeApiAll_mapsLegacyNetworkTypes() {
        Assert.assertEquals(ConnectionType.BLUETOOTH, connectionTypeForLegacyNetwork(ConnectivityManager.TYPE_BLUETOOTH))
        Assert.assertEquals(ConnectionType.ETHERNET, connectionTypeForLegacyNetwork(ConnectivityManager.TYPE_ETHERNET))
        Assert.assertEquals(ConnectionType.VPN, connectionTypeForLegacyNetwork(ConnectivityManager.TYPE_VPN))
        Assert.assertEquals(ConnectionType.UNKNOWN, connectionTypeForLegacyNetwork(-1))
    }

    @Test
    fun getConnectionTypeApiAll_mapsAllLegacyMobileNetworkTypes() {
        listOf(
            ConnectivityManager.TYPE_MOBILE_DUN,
            ConnectivityManager.TYPE_MOBILE_HIPRI,
            ConnectivityManager.TYPE_MOBILE_MMS,
            ConnectivityManager.TYPE_MOBILE_SUPL,
        ).forEach {
            Assert.assertEquals(ConnectionType.CELLULAR_4G, connectionTypeForLegacyNetwork(it, TelephonyManager.NETWORK_TYPE_LTE))
        }
    }

    @Test
    fun getMobileConnection_mapsCellularGenerations() {
        Assert.assertEquals(ConnectionType.CELLULAR_2G, deviceInfo.getMobileConnection(TelephonyManager.NETWORK_TYPE_GPRS))
        Assert.assertEquals(ConnectionType.CELLULAR_3G, deviceInfo.getMobileConnection(TelephonyManager.NETWORK_TYPE_HSPA))
        Assert.assertEquals(ConnectionType.CELLULAR_4G, deviceInfo.getMobileConnection(TelephonyManager.NETWORK_TYPE_LTE))
        Assert.assertEquals(ConnectionType.CELLULAR_5G, deviceInfo.getMobileConnection(TelephonyManager.NETWORK_TYPE_NR))
        Assert.assertEquals(ConnectionType.CELLULAR_UNKNOWN, deviceInfo.getMobileConnection(TelephonyManager.NETWORK_TYPE_UNKNOWN))
    }

    @Test
    fun getMobileConnection_mapsAllGroupedCellularTypes() {
        listOf(
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM,
        ).forEach {
            Assert.assertEquals(ConnectionType.CELLULAR_2G, deviceInfo.getMobileConnection(it))
        }

        listOf(
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        ).forEach {
            Assert.assertEquals(ConnectionType.CELLULAR_3G, deviceInfo.getMobileConnection(it))
        }

        Assert.assertEquals(ConnectionType.CELLULAR_UNKNOWN, deviceInfo.getMobileConnection(TelephonyManager.NETWORK_TYPE_IWLAN))
        Assert.assertEquals(ConnectionType.CELLULAR_UNKNOWN, deviceInfo.getMobileConnection(-1))
    }

    private fun contextWithConfiguration(configuration: Configuration): Context {
        val context: Context = mock()
        val resources: Resources = mock()
        whenever(context.resources).thenReturn(resources)
        whenever(resources.configuration).thenReturn(configuration)
        return context
    }

    private fun connectivityManagerWithTransport(transport: Int?): ConnectivityManager {
        val connectivityManager: ConnectivityManager = mock()
        val network: Network = mock()
        val capabilities: NetworkCapabilities = mock()
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities)
        whenever(capabilities.hasTransport(any())).thenAnswer { invocation ->
            invocation.arguments[0] == transport
        }
        return connectivityManager
    }

    private fun connectionTypeForTransport(transport: Int): ConnectionType {
        return deviceInfo.getConnectionTypeApi23(connectivityManagerWithTransport(transport))
    }

    private fun connectionTypeForLegacyNetwork(type: Int, subtype: Int = TelephonyManager.NETWORK_TYPE_UNKNOWN): ConnectionType {
        val connectivityManager: ConnectivityManager = mock()
        val networkInfo: NetworkInfo = mock()
        whenever(connectivityManager.activeNetworkInfo).thenReturn(networkInfo)
        whenever(networkInfo.isConnected).thenReturn(true)
        whenever(networkInfo.type).thenReturn(type)
        whenever(networkInfo.subtype).thenReturn(subtype)
        return deviceInfo.getConnectionTypeApiAll(connectivityManager)
    }
}
