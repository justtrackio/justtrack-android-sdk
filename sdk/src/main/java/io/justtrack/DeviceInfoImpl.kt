package io.justtrack

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Point
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import io.justtrack.versions.ApplicationVersionImpl
import java.util.Locale

internal class DeviceInfoImpl internal constructor(private val context: Context) : DeviceInfo {
    override fun getConnectionType(): ConnectionType {
        try {
            val manager =
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val connectionType = getConnectionTypeApi23(manager)

                if (connectionType != ConnectionType.UNKNOWN) {
                    return connectionType
                }
            }

            return getConnectionTypeApiAll(manager)
        } catch (exception: RuntimeException) {
            return ConnectionType.UNKNOWN
        }
    }

    /**
     * DO NOT USE THIS FUNCTION TO OBTAIN APP VERSION
     *
     * This is a default method for obtaining the application version.
     * However the application developer may provide their own application version.
     */
    override fun getAppVersion(): ApplicationVersion {
        val packageName = getApplicationPackageName()
        var info: PackageInfo? = null
        try {
            info = context.packageManager.getPackageInfo(packageName, 0)
        } catch (exception: PackageManager.NameNotFoundException) {
            // ignore, we can't do anything
        }
        if (info != null) {
            val versionName = info.versionName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val versionCode = info.longVersionCode
                val minor = (versionCode and UNSIGNED_INT_MASK).toInt()
                val major = (versionCode ushr INT_BIT_SIZE).toInt()
                val versionString = if (major > 0) {
                    "$major.$minor"
                } else {
                    minor.toString()
                }
                return ApplicationVersionImpl(versionName, versionString)
            } else {
                return ApplicationVersionImpl(versionName, info.versionCode.toString() + "")
            }
        } else {
            return ApplicationVersionImpl("", "")
        }
    }

    /**
     * DO NOT USE THIS FUNCTION TO OBTAIN APP PACKAGE NAME
     *
     * This is a default method for obtaining the application package name.
     * However some function such as getInstallSourceInfo required the real application package name can use this.
     */
    override fun getApplicationPackageName(): String {
        return context.packageName
    }

    override fun getAppName(): String? {
        val applicationInfo = context.applicationInfo
        if (applicationInfo != null) {
            val stringId = applicationInfo.labelRes
            var label: String? = null
            if (applicationInfo.nonLocalizedLabel != null) {
                label = applicationInfo.nonLocalizedLabel.toString()
            }
            if (stringId != 0 && label == null) {
                label = context.getString(stringId)
            }

            return label
        }

        return null
    }

    @SuppressLint("HardwareIds")
    override fun getAndroidId(): String? {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun getAndroidIdOrDefault(def: String): String {
        val androidId = getAndroidId()

        return androidId ?: def
    }

    override fun getCountryIso(): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var iso = tm.networkCountryIso
        iso = if (TextUtils.isNullOrEmpty(iso)) null else iso!!.uppercase() // use the root locale!

        // if we end up with a country iso we need to map, do this. We could map something to null,
        // so we need to perform the contains check instead
        if (reservedMap.containsKey(iso)) {
            return reservedMap[iso]
        }

        return iso
    }

    override fun getDeviceLocale(): String? {
        val locales = getLocales(context)
        if (locales.isEmpty()) {
            return null
        }

        val language = locales[0].language

        return if (TextUtils.isNullOrEmpty(language)) null else language.lowercase()
    }

    @VisibleForTesting
    internal fun getLocales(context: Context): List<Locale> {
        val locales: MutableList<Locale> = ArrayList()
        val config = context.resources.configuration

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            locales.add(config.locale)
        } else {
            val list = config.locales

            for (i in 0 until list.size()) {
                locales.add(list[i])
            }
        }

        return locales
    }

    override fun getDeviceType(): DeviceType = if (context.resources.configuration.smallestScreenWidthDp >= TABLET_SCREEN_DENSITY) {
        DeviceType.TABLET
    } else {
        DeviceType.PHONE
    }

    override val deviceName: String
        get() = Build.DEVICE

    override val deviceModel: String
        get() = Build.MODEL

    override val deviceProduct: String
        get() = Build.PRODUCT

    override val osVersion: String
        get() = Build.VERSION.RELEASE

    override val osName: String
        get() = "Android"

    override val osLevel: Int
        get() = Build.VERSION.SDK_INT

    override val build: String
        get() = Build.ID

    @SuppressLint("ObsoleteSdkInt")
    override fun getDisplaySize(): Point {
        val size = Point(0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val display = context.display

                if (display != null) {
                    display.getRealSize(size)

                    return size
                }
            } catch (exception: UnsupportedOperationException) {
                // ignore and fall through to the old code
                // we might be running in an integration test without a display
                // or for some reason we don't have one attached to the context
            }
        }
        val currentActivityContext = activityContext
        val context = currentActivityContext ?: context
        // Strict-Mode: Avoid strictMode by using activityContext instead of ApplicationContext.
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val display = wm.defaultDisplay

        display?.getRealSize(size)

        return size
    }

    override val cpuArch: String?
        get() {
            val arch = System.getProperty("os.arch")
            if (arch != null) {
                return arch
            }

            if (Build.SUPPORTED_ABIS.size > 0) {
                return Build.SUPPORTED_ABIS[0]
            }

            return null
        }

    private val activityContext: Context?
        get() = JustTrack.getCurrentActivity()

    @VisibleForTesting
    @RequiresApi(api = Build.VERSION_CODES.M)
    internal fun getConnectionTypeApi23(manager: ConnectivityManager): ConnectionType {
        val network = manager.activeNetwork ?: return ConnectionType.UNKNOWN

        val capabilities = manager.getNetworkCapabilities(network) ?: return ConnectionType.UNKNOWN

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return getConnectionTypeApiAll(manager)
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return ConnectionType.WIFI
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return ConnectionType.BLUETOOTH
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return ConnectionType.ETHERNET
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return ConnectionType.VPN
        }

        return ConnectionType.UNKNOWN
    }

    @VisibleForTesting
    internal fun getConnectionTypeApiAll(manager: ConnectivityManager): ConnectionType {
        val info = manager.activeNetworkInfo ?: return ConnectionType.UNKNOWN

        if (!info.isConnected) {
            return ConnectionType.OFFLINE
        }

        return when (info.type) {
            ConnectivityManager.TYPE_BLUETOOTH -> ConnectionType.BLUETOOTH
            ConnectivityManager.TYPE_ETHERNET -> ConnectionType.ETHERNET

            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_MOBILE_DUN,
            ConnectivityManager.TYPE_MOBILE_HIPRI,
            ConnectivityManager.TYPE_MOBILE_MMS,
            ConnectivityManager.TYPE_MOBILE_SUPL,
            -> getMobileConnection(
                info.subtype,
            )

            ConnectivityManager.TYPE_VPN -> ConnectionType.VPN
            ConnectivityManager.TYPE_WIFI -> ConnectionType.WIFI
            else -> ConnectionType.UNKNOWN
        }
    }

    @VisibleForTesting
    internal fun getMobileConnection(networkType: Int): ConnectionType {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM,
            -> ConnectionType.CELLULAR_2G

            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            -> ConnectionType.CELLULAR_3G

            TelephonyManager.NETWORK_TYPE_LTE -> ConnectionType.CELLULAR_4G

            TelephonyManager.NETWORK_TYPE_NR -> ConnectionType.CELLULAR_5G

            TelephonyManager.NETWORK_TYPE_UNKNOWN, TelephonyManager.NETWORK_TYPE_IWLAN -> ConnectionType.CELLULAR_UNKNOWN
            else -> ConnectionType.CELLULAR_UNKNOWN
        }
    }

    companion object {

        private const val TABLET_SCREEN_DENSITY = 600
        private const val UNSIGNED_INT_MASK = 0xFFFFFFFFL
        private const val INT_BIT_SIZE = 32

        private val reservedMap: Map<String?, String?>

        init {
            val map: MutableMap<String?, String?> = HashMap()

            map["AC"] = "GB"
            map["CP"] = "FR"
            map["CQ"] = "GB"
            map["DG"] = "GB"
            map["EA"] = "ES"
            map["EU"] = null // european union
            map["EZ"] = null // euro zone
            map["FX"] = "FR"
            map["IC"] = "ES"
            map["SU"] = "RU" // soviet union
            map["TA"] = "GB"
            map["UK"] = "GB" // united kingdom
            map["UN"] = null // united nations

            reservedMap = map
        }
    }
}
