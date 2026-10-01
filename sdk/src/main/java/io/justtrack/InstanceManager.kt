package io.justtrack

import androidx.annotation.VisibleForTesting
import java.lang.ref.WeakReference

internal object InstanceManager {
    private var instance: WeakReference<JustTrackSdkImpl>? = null

    @JvmStatic
    fun clearInstance(sdk: JustTrackSdkImpl) {
        // Deadlock-Safety: We only hold the lock while accessing a weak reference and don't make
        // any other calls - calls to the logger are on purpose after the synchronized block
        val logMessage: String? = synchronized(InstanceManager::class.java) {
            val existing = instance?.get()
            when {
                existing == null -> {
                    instance = null
                    "Clearing non-existing SDK instance"
                }

                existing !== sdk -> "Not clearing second SDK instance"

                else -> {
                    instance = null
                    null
                }
            }
        }

        if (logMessage != null) {
            sdk.logger.warn(logMessage)
        }
    }

    fun getInstance(builder: JustTrackSdkBuilder): JustTrackSdkImpl {
        val sdk: JustTrackSdkImpl

        // Deadlock-Safety: We just check for a new instance and if none is there, we construct one.
        // All the heavy initialization happens later outside the synchronized block.
        synchronized(InstanceManager::class.java) {
            val currentSdk = getInstance()
            if (currentSdk != null) {
                return currentSdk
            }

            sdk = JustTrackSdkImpl(builder)
            setInstance(sdk)
        }

        // finish the initialization of the new SDK instance
        sdk.init(builder)

        if (builder.isEnableUncaughtExceptionHandler) {
            sdk.installUncaughtExceptionHandler()
        }

        if (!builder.isManualStart) {
            sdk.start()
        }

        return sdk
    }

    @JvmStatic
    @Synchronized
    fun getInstance(): JustTrackSdkImpl? = instance?.get()

    @VisibleForTesting
    fun setInstance(sdk: JustTrackSdkImpl) {
        instance = WeakReference<JustTrackSdkImpl>(sdk)
    }
}
