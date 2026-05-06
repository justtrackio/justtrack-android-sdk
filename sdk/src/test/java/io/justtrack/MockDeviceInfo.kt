package io.justtrack

import android.graphics.Point
import io.justtrack.versions.ApplicationVersionImpl

internal class MockDeviceInfo : DeviceInfo {
    override fun getConnectionType(): ConnectionType {
        return ConnectionType.WIFI
    }

    override fun getAppVersion(): ApplicationVersion {
        return ApplicationVersionImpl("1.0.0", "1")
    }

    override fun getAppName(): String {
        return "appName"
    }

    override fun getApplicationPackageName(): String {
        return "io.appname"
    }

    override fun getAndroidId(): String {
        return "androidId"
    }

    override fun getAndroidIdOrDefault(def: String): String {
        return "androidId"
    }

    override fun getCountryIso(): String {
        return "DE"
    }

    override fun getDeviceLocale(): String {
        return "EN"
    }

    override fun getDeviceType(): DeviceType {
        return DeviceType.PHONE
    }

    override fun getDisplaySize(): Point {
        return Point(1, 1)
    }

    override val deviceName: String
        get() = "samsung"
    override val deviceModel: String
        get() = "samsung A1"
    override val deviceProduct: String
        get() = "deviceProduct"
    override val osVersion: String
        get() = "7.0"
    override val osName: String
        get() = "Android"
    override val osLevel: Int
        get() = 15
    override val cpuArch: String
        get() = "arm"
    override val build: String
        get() = "DeviceBuild"
}
