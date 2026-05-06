package io.justtrack

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import io.justtrack.versions.ApplicationVersionImpl
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal open class BaseDTOTest {
    private val ctx: Context = mock<Context>()
    private val mgr: PackageManager = mock<PackageManager>()
    private val di: DeviceInfoImpl = mock<DeviceInfoImpl>()

    val context: Context
        get() {
            whenever(ctx.packageManager).thenReturn(mgr)

            return ctx
        }

    val deviceInfoImpl: DeviceInfoImpl
        get() {
            whenever(di.getAndroidIdOrDefault("")).thenReturn("android id")
            whenever(di.getAndroidId()).thenReturn("android id")
            whenever(di.getConnectionType()).thenReturn(ConnectionType.CELLULAR_5G)
            whenever(di.getCountryIso()).thenReturn("country iso")
            whenever(di.deviceName).thenReturn("device name")
            whenever(di.deviceModel).thenReturn("device model")
            whenever(di.deviceProduct).thenReturn("device product")
            whenever(di.getDeviceType()).thenReturn(DeviceType.PHONE)
            whenever(di.osVersion).thenReturn("os version")
            whenever(di.osLevel).thenReturn(15)
            whenever(di.getDisplaySize()).thenReturn(Point(1920, 1080))
            whenever(di.getAppVersion()).thenReturn(ApplicationVersionImpl("2.7-Test", "270"))
            whenever(di.getDeviceLocale()).thenReturn("device locale")
            whenever(di.osName).thenReturn("Android")
            return di
        }
}
