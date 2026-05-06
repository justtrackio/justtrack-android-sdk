package io.justtrack

import android.app.Activity
import android.content.Context
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdImpressionState

/**
 * This class is intended for internal use by integrations.
 */
@Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")
class ActivityBasedAdSdkIntegration(
    context: Context,
    private val activityToTrack: Class<out Activity?>,
    private val integrationName: String,
    private val sdk: JustTrackSdk,
) {
    private var state: State
    private var timerStarted: Long = 0
    private var elapsedTime: Long = 0

    init {
        this.state = State.Hidden
        recoverState(context, sdk)
    }

    fun onResume(activity: Activity) {
        when (state) {
            State.Hidden -> if (activity.javaClass == activityToTrack) {
                state = State.Showing
                timerStarted = System.currentTimeMillis()
                elapsedTime = 0
                saveState(activity.application)
            }

            State.Shown -> if (activity.javaClass == activityToTrack) {
                state = State.Showing
                timerStarted = System.currentTimeMillis()
                saveState(activity.application)
            } else {
                state = State.Hidden
                reportImpression(sdk, elapsedTime)
                timerStarted = 0
                elapsedTime = 0
                saveState(activity.application)
            }

            else -> {}
        }
    }

    fun onPause(activity: Activity) {
        if (state == State.Showing && activity.javaClass == activityToTrack) {
            state = State.Shown
            elapsedTime = System.currentTimeMillis() - timerStarted
            saveState(activity.application)
        }
    }

    private fun reportImpression(sdk: JustTrackSdk, elapsedTime: Long) {
        sdk.forwardAdImpression(
            AdImpression(
                "impression",
                integrationName,
                integrationName,
                null,
                null,
                null,
                null,
                null,
                null,
                state = if (elapsedTime > MIN_IMPRESSION_MILLIS) AdImpressionState.COMPLETED else AdImpressionState.SKIPPED,
            ),
        )
    }

    private fun saveState(context: Context) {
        context.getSharePrefWithIO<Any?>(
            NAME_PREFIX + integrationName,
            Context.MODE_PRIVATE,
        ) {
            edit()
                .putBoolean(IMPRESSION_ACTIVE_KEY, state != State.Hidden)
                .putLong(ELAPSED_TIME_KEY, elapsedTime)
                .apply()
            null
        }
    }

    private fun recoverState(context: Context, sdk: JustTrackSdk) {
        context.getSharePrefWithIO<Any?>(
            NAME_PREFIX + integrationName,
            Context.MODE_PRIVATE,
        ) {
            val impressionActiveKey = getBoolean(
                IMPRESSION_ACTIVE_KEY,
                false,
            )
            if (impressionActiveKey) {
                reportImpression(sdk, getLong(ELAPSED_TIME_KEY, 0))
            }
            // throw away any impression we just reported - otherwise we might report it twice.
            edit().clear().apply()
            null
        }
    }

    private enum class State {
        Hidden,
        Showing,
        Shown,
    }

    companion object {
        private const val NAME_PREFIX = "io.justtrack.sdk.adIntegration."
        private const val IMPRESSION_ACTIVE_KEY = "impressionActive"
        private const val ELAPSED_TIME_KEY = "elapsedTime"

        // Minimum amount of time a video ad needs to be displayed until we count it as completed.
        // Ad SDKs often allow the user to skip after 6s, so if the user watches longer, we assume the user didn't (directly)
        // skip the ad (of course, there is no way to distinguish a user watching only 20s of a 30s or a user completely
        // watching a 15s ad).
        private const val MIN_IMPRESSION_MILLIS: Long = 7000
    }
}
