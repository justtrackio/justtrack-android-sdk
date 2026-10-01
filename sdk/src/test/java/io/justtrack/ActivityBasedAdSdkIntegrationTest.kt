package io.justtrack

import android.app.Activity
import android.content.Context
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdImpressionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ActivityBasedAdSdkIntegrationTest {

    class TrackedActivity : Activity()
    class OtherActivity : Activity()

    private lateinit var context: Context
    private lateinit var sdk: JustTrackSdk

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        sdk = mock {
            on { forwardAdImpression(any()) } doReturn mock<AsyncFuture<Void?>>()
        }
        // Clean up any previously persisted state for the integration name we use.
        context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun newIntegration(): ActivityBasedAdSdkIntegration =
        ActivityBasedAdSdkIntegration(context, TrackedActivity::class.java, "testIntegration", sdk)

    private fun tracked(): Activity = Robolectric.buildActivity(TrackedActivity::class.java).get()
    private fun other(): Activity = Robolectric.buildActivity(OtherActivity::class.java).get()

    private fun captureImpression(): AdImpression {
        val captor = argumentCaptor<AdImpression>()
        verify(sdk).forwardAdImpression(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `onResume from Hidden with non-tracked activity is no-op`() {
        val integration = newIntegration()
        integration.onResume(other())
        verify(sdk, never()).forwardAdImpression(any())
        val prefs = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        // Nothing was saved because state stayed Hidden.
        assertFalse(prefs.getBoolean("impressionActive", false))
    }

    @Test
    fun `onResume from Hidden with tracked activity starts Showing and persists active state`() {
        val integration = newIntegration()
        integration.onResume(tracked())

        val prefs = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("impressionActive", false))
        assertEquals(0L, prefs.getLong("elapsedTime", -1L))
        verify(sdk, never()).forwardAdImpression(any())
    }

    @Test
    fun `onPause from Showing with tracked activity transitions to Shown and records elapsed`() {
        val integration = newIntegration()
        integration.onResume(tracked())
        Thread.sleep(5)
        integration.onPause(tracked())

        val prefs = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("impressionActive", false))
        assertTrue("elapsedTime should be > 0", prefs.getLong("elapsedTime", 0L) >= 0L)
    }

    @Test
    fun `onPause from Showing with non-tracked activity is no-op`() {
        val integration = newIntegration()
        integration.onResume(tracked())
        // Pausing a different activity while showing should not change state.
        integration.onPause(other())

        val prefs = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        // State remains Showing => impressionActive true, elapsedTime 0
        assertTrue(prefs.getBoolean("impressionActive", false))
        assertEquals(0L, prefs.getLong("elapsedTime", -1L))
    }

    @Test
    fun `onResume from Shown with tracked activity returns to Showing without reporting`() {
        val integration = newIntegration()
        integration.onResume(tracked())
        integration.onPause(tracked()) // Shown
        integration.onResume(tracked()) // back to Showing

        verify(sdk, never()).forwardAdImpression(any())
    }

    @Test
    fun `onResume from Shown with non-tracked activity reports SKIPPED impression for short view`() {
        val integration = newIntegration()
        integration.onResume(tracked())
        integration.onPause(tracked()) // Shown with tiny elapsed time
        integration.onResume(other()) // triggers reportImpression

        val impression = captureImpression()
        assertEquals("impression", impression.unit)
        assertEquals("testIntegration", impression.sdkName)
        assertEquals("testIntegration", impression.network)
        assertEquals(AdImpressionState.SKIPPED, impression.state)

        val prefs = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean("impressionActive", true))
        assertEquals(0L, prefs.getLong("elapsedTime", -1L))
    }

    @Test
    fun `recoverState reports SKIPPED impression for short stored elapsed and clears prefs`() {
        // Pre-populate prefs as if a previous run had an active impression.
        val prefs = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("impressionActive", true).putLong("elapsedTime", 100L).commit()

        newIntegration()

        val impression = captureImpression()
        assertEquals(AdImpressionState.SKIPPED, impression.state)
        // Prefs cleared after recovery (re-fetch the SharedPreferences view).
        val cleared = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        assertFalse("impressionActive key should be cleared", cleared.contains("impressionActive"))
        assertFalse("elapsedTime key should be cleared", cleared.contains("elapsedTime"))
    }

    @Test
    fun `recoverState reports COMPLETED impression when stored elapsed exceeds threshold`() {
        val prefs = context.getSharedPreferences("io.justtrack.sdk.adIntegration.testIntegration", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("impressionActive", true).putLong("elapsedTime", 8000L).commit()

        newIntegration()

        val impression = captureImpression()
        assertEquals(AdImpressionState.COMPLETED, impression.state)
        assertNotNull(impression)
    }

    @Test
    fun `recoverState with no persisted active impression does nothing`() {
        // SharedPreferences for this integration is empty (cleared in setUp).
        newIntegration()
        verify(sdk, never()).forwardAdImpression(any())
    }

    @Test
    fun `onPause from Hidden state is no-op`() {
        val integration = newIntegration()
        // No prior onResume => state is Hidden. Pausing a non-tracked activity should be no-op too.
        integration.onPause(other())
        verify(sdk, never()).forwardAdImpression(any())
    }

    @Test
    fun `onResume from Showing state is no-op`() {
        val integration = newIntegration()
        integration.onResume(tracked()) // Hidden -> Showing
        integration.onResume(tracked()) // Showing -> hits else branch, no-op
        integration.onResume(other()) // also no-op while Showing
        verify(sdk, never()).forwardAdImpression(any())
    }
}
