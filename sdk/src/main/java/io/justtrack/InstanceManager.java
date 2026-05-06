package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;

class InstanceManager {
    @Nullable
    private static WeakReference<JustTrackSdkImpl> instance;

    static void clearInstance(@NonNull JustTrackSdkImpl sdk) {
        final String logMessage;

        // Deadlock-Safety: We only hold the lock while accessing a weak reference and don't make
        // any other calls - calls to the logger are on purpose after the synchronized block
        synchronized (InstanceManager.class) {
            if (instance == null) {
                logMessage = "Clearing non-existing SDK instance";
            } else {
                @Nullable JustTrackSdkImpl existing = instance.get();
                if (existing == null) {
                    logMessage = "Clearing non-existing SDK instance";
                    instance = null;
                } else if (existing != sdk) {
                    logMessage = "Not clearing second SDK instance";
                } else {
                    logMessage = null;
                    instance = null;
                }
            }
        }

        if (logMessage != null) {
            sdk.logger.warn(logMessage);
        }
    }

    @NonNull
    static JustTrackSdkImpl getInstance(@NonNull JustTrackSdkBuilder builder) {
        final JustTrackSdkImpl sdk;

        // Deadlock-Safety: We just check for a new instance and if none is there, we construct one.
        // All the heavy initialization happens later outside the synchronized block.
        synchronized (InstanceManager.class) {
            JustTrackSdkImpl currentSdk = getInstance();

            if (currentSdk != null) {
                return currentSdk;
            }

            sdk = new JustTrackSdkImpl(builder);
            setInstance(sdk);
        }

        // finish the initialization of the new SDK instance
        sdk.init(builder);

        if (builder.isEnableUncaughtExceptionHandler()) {
            sdk.installUncaughtExceptionHandler();
        }

        if (!builder.isManualStart()) {
            sdk.start();
        }

        return sdk;
    }

    @Nullable
    // Deadlock-Safety: This is a small accessor which only reads a weak reference
    static synchronized JustTrackSdkImpl getInstance() {
        if (instance != null) {
            return instance.get();
        }

        return null;
    }

    @VisibleForTesting
    static void setInstance(@NonNull JustTrackSdkImpl sdk) {
        instance = new WeakReference<>(sdk);
    }
}
