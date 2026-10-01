package io.justtrack.config

import android.content.Context
import androidx.annotation.VisibleForTesting
import org.json.JSONObject

internal class RemoteConfigStoreImpl internal constructor(
    context: Context,
) : RemoteConfigStore {
    private val store = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    override fun getCurrentFetchInterval(): Long {
        return store.getLong(STORE_INTERVAL_KEY, DEFAULT_MIN_FETCH_INTERVAL)
    }

    override fun getPreviousFetchTimeStamp(): Long? {
        val previousFetch = store.getLong(STORE_LAST_FETCH_KEY, -1L)
        return if (previousFetch == -1L) {
            null
        } else {
            previousFetch
        }
    }

    override fun setPreviousFetchTimeStamp(previousFetchTimeStamp: Long) {
        store.edit().putLong(STORE_LAST_FETCH_KEY, previousFetchTimeStamp).apply()
    }

    override fun setMinimumIntervalInSecond(minimumIntervalInSecond: Long) {
        store.edit()
            .putLong(STORE_INTERVAL_KEY, minimumIntervalInSecond)
            .apply()
    }

    override fun getRetryAfterSeconds(): Int? {
        return if (store.contains(STORE_RETRY_AFTER_SECONDS_KEY)) {
            store.getInt(STORE_RETRY_AFTER_SECONDS_KEY, CONSUMED_RETRY_AFTER)
        } else {
            null
        }
    }

    override fun setRetryAfterSeconds(retryAfterSeconds: Int) {
        store.edit().putInt(STORE_RETRY_AFTER_SECONDS_KEY, retryAfterSeconds).apply()
    }

    override fun setStoredAssignments(assignment: String?) {
        if (assignment == null) {
            store.edit().remove(STORE_ASSIGNMENTS_KEY).apply()
        } else {
            store.edit()
                .putString(STORE_ASSIGNMENTS_KEY, assignment)
                .apply()
        }
    }

    override fun getStoredAssignments(): Map<String, Assignment>? {
        val storeAssignmentsRaw = store.getString(STORE_ASSIGNMENTS_KEY, null)

        return if (storeAssignmentsRaw == null) {
            null
        } else {
            Assignment.parseAssignments(JSONObject(storeAssignmentsRaw)).associateBy { it.configKey }
        }
    }

    companion object {
        @VisibleForTesting
        internal const val DEFAULT_MIN_FETCH_INTERVAL = 60 * 60L // 1 hour
        internal const val CONSUMED_RETRY_AFTER = -1

        @VisibleForTesting
        const val STORE_NAME = "justtrack-remote-config"
        private const val STORE_INTERVAL_KEY = "remote_config_interval"
        private const val STORE_LAST_FETCH_KEY = "remote_config_last_fetch"
        private const val STORE_RETRY_AFTER_SECONDS_KEY = "remote_config_retry_after_seconds"

        @VisibleForTesting
        internal const val STORE_ASSIGNMENTS_KEY = "remote_config_stored_assignments"
    }
}
