package io.justtrack;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

class ActivityLifecycleListener implements Application.ActivityLifecycleCallbacks {
    @NonNull
    private final Map<Activity, ActivityState> stateMap;
    @Nullable
    private WeakReference<Activity> lastActiveActivity;

    ActivityLifecycleListener() {
        this.stateMap = new WeakHashMap<>();
        this.lastActiveActivity = null;
    }

    // Deadlock-Safety: Leaf-lock. Only calls methods on a HashMap and WeakReference.
    @Nullable
    synchronized Activity getLastActivity() {
        for (Map.Entry<Activity, ActivityState> entry : stateMap.entrySet()) {
            if (entry.getValue() == ActivityState.RESUMED) {
                return entry.getKey();
            }
        }

        return lastActiveActivity != null ? lastActiveActivity.get() : null;
    }

    // Deadlock-Safety: Leaf-lock. Only calls methods on a HashMap.
    @Override
    public synchronized void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        stateMap.put(activity, ActivityState.CREATED);
    }

    // Deadlock-Safety: Leaf-lock. Only calls methods on a HashMap.
    @Override
    public synchronized void onActivityStarted(@NonNull Activity activity) {
        stateMap.put(activity, ActivityState.STARTED);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        // Deadlock-Safety: Leaf-lock. Only calls methods on a HashMap.
        synchronized (this) {
            stateMap.put(activity, ActivityState.RESUMED);
            lastActiveActivity = new WeakReference<>(activity);
        }
        JustTrackSdkImpl sdkRef = InstanceManager.getInstance();
        if (sdkRef != null) {
            sdkRef.onResume(activity);
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // Deadlock-Safety: Leaf-lock. Only calls methods on a HashMap.
        synchronized (this) {
            stateMap.put(activity, ActivityState.PAUSED);
        }
        // we can use the global SDK instance as there is only one activity lifecycle listener (from us) in the app
        JustTrackSdkImpl sdkRef = InstanceManager.getInstance();
        if (sdkRef != null) {
            sdkRef.onPause();
        }
    }

    // Deadlock-Safety: Leaf-lock. Only calls methods on a HashMap.
    @Override
    public synchronized void onActivityStopped(@NonNull Activity activity) {
        stateMap.put(activity, ActivityState.STOPPED);
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    // Deadlock-Safety: Leaf-lock. Only calls methods on a HashMap.
    @Override
    public synchronized void onActivityDestroyed(@NonNull Activity activity) {
        stateMap.remove(activity);
    }

    private enum ActivityState {
        CREATED,
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED
    }
}
