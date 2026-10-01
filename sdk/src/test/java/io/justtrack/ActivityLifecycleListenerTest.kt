package io.justtrack

import android.app.Activity
import android.os.Bundle
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class ActivityLifecycleListenerTest {

    @After
    fun tearDown() {
        // Make sure no stale SDK instance leaks between tests.
        ReflectionHelpers.setStaticField(InstanceManager::class.java, "instance", null)
    }

    @Test
    fun getLastActivity_returnsNullWhenNoActivityRegistered() {
        val listener = ActivityLifecycleListener()

        assertNull(listener.lastActivity)
    }

    @Test
    fun onActivityCreated_doesNotMarkActivityAsResumed() {
        val listener = ActivityLifecycleListener()
        val activity: Activity = mock()

        listener.onActivityCreated(activity, null as Bundle?)

        // CREATED doesn't satisfy RESUMED scan and there is no lastActiveActivity reference yet.
        assertNull(listener.lastActivity)
    }

    @Test
    fun onActivityStarted_doesNotMarkActivityAsResumed() {
        val listener = ActivityLifecycleListener()
        val activity: Activity = mock()

        listener.onActivityStarted(activity)

        assertNull(listener.lastActivity)
    }

    @Test
    fun onActivityResumed_marksAsLastActivity() {
        val listener = ActivityLifecycleListener()
        val activity: Activity = mock()

        listener.onActivityResumed(activity)

        assertSame(activity, listener.lastActivity)
    }

    @Test
    fun onActivityResumed_returnsResumedActivityEvenIfOthersExist() {
        val listener = ActivityLifecycleListener()
        val resumed: Activity = mock()
        val stopped: Activity = mock()
        listener.onActivityStarted(stopped)
        listener.onActivityResumed(resumed)
        listener.onActivityStopped(stopped)

        assertSame(resumed, listener.lastActivity)
    }

    @Test
    fun onActivityPaused_keepsLastActiveActivityReferenceViaWeakRef() {
        val listener = ActivityLifecycleListener()
        val activity: Activity = mock()
        listener.onActivityResumed(activity)
        listener.onActivityPaused(activity)

        // No RESUMED entry remains; the fallback weak ref to lastActiveActivity is returned.
        assertSame(activity, listener.lastActivity)
    }

    @Test
    fun onActivitySaveInstanceState_isNoOp() {
        val listener = ActivityLifecycleListener()
        val activity: Activity = mock()

        listener.onActivitySaveInstanceState(activity, Bundle())

        assertNull(listener.lastActivity)
    }

    @Test
    fun onActivityDestroyed_removesActivityFromState() {
        val listener = ActivityLifecycleListener()
        val activity: Activity = mock()
        listener.onActivityResumed(activity)

        listener.onActivityDestroyed(activity)

        // The activity was removed from the state map but lastActiveActivity weak ref still holds it.
        assertSame(activity, listener.lastActivity)
    }

    @Test
    fun fullLifecycleSequence_advancesThroughAllStates() {
        val listener = ActivityLifecycleListener()
        val activity: Activity = mock()

        listener.onActivityCreated(activity, null as Bundle?)
        listener.onActivityStarted(activity)
        listener.onActivityResumed(activity)
        assertSame(activity, listener.lastActivity)

        listener.onActivityPaused(activity)
        listener.onActivityStopped(activity)
        listener.onActivityDestroyed(activity)
        // After destroy, the WeakReference (still strongly held by lastActiveActivity field) keeps activity reachable.
        assertSame(activity, listener.lastActivity)
    }
}
