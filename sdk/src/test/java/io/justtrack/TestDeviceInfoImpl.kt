package io.justtrack

import android.graphics.Point
import io.justtrack.versions.ApplicationVersionImpl
import java.util.UUID

internal open class TestDeviceInfoImpl : DeviceInfo {
    override fun getConnectionType(): ConnectionType {
        return ConnectionType.WIFI
    }

    override fun getAppVersion(): ApplicationVersion {
        return ApplicationVersionImpl("7.0.0", "7000")
    }

    override fun getAppName(): String? {
        return "justtrack"
    }

    override fun getApplicationPackageName(): String {
        return "io.justtrack"
    }

    override fun getAndroidId(): String? {
        return UUID.randomUUID().toString()
    }

    override fun getAndroidIdOrDefault(def: String): String {
        return UUID.randomUUID().toString()
    }

    override fun getCountryIso(): String? {
        return "DE"
    }

    override fun getDeviceLocale(): String? {
        return "EN"
    }

    override fun getDeviceType(): DeviceType {
        return DeviceType.PHONE
    }

    override fun getDisplaySize(): Point {
        return Point(100, 100)
    }

    override val deviceName: String
        get() = "Samsung Justtrack"
    override val deviceModel: String
        get() = "Samsumg A1"
    override val deviceProduct: String
        get() = ""
    override val osVersion: String
        get() = "6"
    override val osName: String
        get() = "Android"
    override val osLevel: Int
        get() = 5
    override val cpuArch: String?
        get() = null
    override val build: String
        get() = "build"
}
