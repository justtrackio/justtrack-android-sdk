package io.justtrack

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import io.justtrack.log.Logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Entry point for the justtrack SDK lifecycle. Provides access to the current SDK instance
 * and handles internal initialization triggered by [InitializerContentProvider].
 */
class JustTrack private constructor() {

    /** Provides static access to the SDK instance and app-start notifications. */
    companion object {
        private val appStartedAt = AtomicLong(System.currentTimeMillis())
        private val started = AtomicBoolean(false)
        private var activityLifecycleListener: ActivityLifecycleListener? = null

        /**
         * Retrieve the current instance of the {@link JusttrackSdk} if one is available. If no instance
         * was created yet or it was destroyed again, this method returns null.
         *
         * @return The current SDK instance or null.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(): JustTrackSdk? = InstanceManager.getInstance()

        /**
         * Notifies the SDK that the application has started, recording the start timestamp
         * for app-start duration tracking. Safe to call multiple times; only the first
         * invocation records the timestamp.
         */
        @JvmStatic
        @Synchronized
        fun notifyAppStart() {
            appStartedAt.compareAndSet(0, System.currentTimeMillis())
        }

        @JvmStatic
        @JvmName("init")
        @Synchronized
        internal fun init(context: Context, logger: Logger?) {
            notifyAppStart()
            if (context is Application) {
                initActivityLifecycleListener(context, logger)
            }
        }

        @JvmStatic
        @JvmName("initWithSdk")
        @Synchronized
        internal fun initWithSdk(application: Context, sdk: JustTrackSdkImpl) {
            if (activityLifecycleListener == null) {
                initActivityLifecycleListener(application, sdk.logger)
            }
        }

        @JvmStatic
        @JvmName("getCurrentActivity")
        @Synchronized
        internal fun getCurrentActivity(): Activity? {
            return activityLifecycleListener?.lastActivity
        }

        @JvmStatic
        @JvmName("notifyQueuedEvents")
        @Synchronized
        internal fun notifyQueuedEvents() {
            val sdkImpl = InstanceManager.getInstance()
            if (sdkImpl != null) {
                val appStartDurationTracker = getAppStartDuration()
                if (appStartDurationTracker != null) {
                    sdkImpl.notifyAppStart(appStartDurationTracker)
                }
            }
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        @JvmStatic
        @JvmName("resetForTesting")
        @Synchronized
        internal fun resetForTesting() {
            started.set(false)
        }

        private fun initActivityLifecycleListener(application: Context, logger: Logger?) {
            activityLifecycleListener = ActivityLifecycleListener()
            try {
                (application as Application).registerActivityLifecycleCallbacks(activityLifecycleListener)
            } catch (exception: Throwable) {
                logger?.warn("Failed to register activity lifecycle listener", exception)
            }
        }

        private fun getAppStartDuration(): AppStartDuration? {
            val startedAt = appStartedAt.get()

            if (startedAt == 0L) {
                return null
            }

            if (started.getAndSet(true)) {
                return null
            }

            return AppStartDuration(startedAt)
        }
    }
}
