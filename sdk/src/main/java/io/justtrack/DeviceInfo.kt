package io.justtrack

import android.graphics.Point

internal interface DeviceInfo {
    fun getConnectionType(): ConnectionType

    /**
     * DO NOT USE THIS FUNCTION TO OBTAIN APP VERSION
     *
     * This is a default method for obtaining the application version.
     * However the application developer may provide their own application version.
     */
    fun getAppVersion(): ApplicationVersion
    fun getAppName(): String?
    fun getApplicationPackageName(): String
    fun getAndroidId(): String?
    fun getAndroidIdOrDefault(def: String): String
    fun getCountryIso(): String?
    fun getDeviceLocale(): String?
    fun getDeviceType(): DeviceType
    fun getDisplaySize(): Point

    val deviceName: String
    val deviceModel: String
    val deviceProduct: String
    val osVersion: String
    val osName: String
    val osLevel: Int
    val cpuArch: String?
    val build: String
}
