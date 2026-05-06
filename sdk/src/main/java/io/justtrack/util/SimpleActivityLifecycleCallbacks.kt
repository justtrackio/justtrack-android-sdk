package io.justtrack.util

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/**
 * This class is intended for internal use.
 */
abstract class SimpleActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
