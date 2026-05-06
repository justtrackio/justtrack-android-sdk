package io.justtrack

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting

internal abstract class PersistentIdStore {

    protected abstract fun getSharePrefName(): String

    @VisibleForTesting
    fun clearForTesting(context: Context) {
        val preferences = context.getSharedPreferences(getSharePrefName(), Context.MODE_PRIVATE)
        preferences.edit().clear().apply()
    }

    /**
     * Store a new id. If the id was not yet submitted to the backend, returns true, otherwise false
     * (so we avoid submitting the same id multiple times).
     *
     * @param context   The context to get some shared prefs from.
     * @param installId The installId at the time we got the new request.
     * @param id        The id to submit if not stored.
     * @return true if the id needs to be submitted to the backend, false otherwise.
     */
    fun storeNewId(context: Context, installId: String?, id: String): Boolean {
        val state = State(context, getSharePrefName())
        if (id == state.storedId && installId != null && installId == state.installId) {
            return false
        }

        state.storePending(installId, id)

        return true
    }

    /**
     * Get any pending id which is currently not in the progress of being send to the backend.
     *
     * @param context The context to get some shared prefs from.
     * @return Any pending id which was not yet set as stored on the backend.
     */
    fun getPendingId(context: Context): String? {
        val state = State(context, getSharePrefName())
        if (state.pendingId != null && state.pendingId != state.storedId) {
            return state.pendingId
        }

        return null
    }

    /**
     * Mark an id as stored on the backend.
     *
     * @param context   The context to get some shared prefs from.
     * @param installId The installId we sent to the backend.
     * @param storedId  The id to mark as stored on the backend.
     */
    fun setStoredAtBackend(context: Context, installId: String, storedId: String) {
        State(context, getSharePrefName()).storeStored(installId, storedId)
    }

    /**
     * Get a pending custom user id after an installId change. Once the installId changes, we send the
     * last custom user id again to the backend.
     *
     * @param context   The context to get some shared prefs from.
     * @param installId The new installId value.
     * @return Any pending id which we need to send to the backend.
     */
    fun getPendingWithNewInstallId(context: Context, installId: String): String? {
        val state = State(context, getSharePrefName())
        val nextId = if (state.pendingId == null) state.storedId else state.pendingId
        if (nextId == null || installId == state.installId) {
            return null
        }

        state.clearStored()

        return nextId
    }

    private class State(context: Context, sharedPrefName: String) {
        private val preferences: SharedPreferences = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        var installId: String? = null
        var pendingId: String? = null
        var storedId: String? = null

        init {
            val version = preferences.getInt(KEY_DATA_VERSION, -1)
            if (version != VERSION) {
                if (version != -1) {
                    preferences.edit().clear().apply()
                }
                installId = null
                pendingId = null
                storedId = null
            } else {
                try {
                    val installIdString = preferences.getString(KEY_INSTALL_ID, null)
                    if (installIdString != null) {
                        installId = installIdString
                    }
                } catch (exception: IllegalArgumentException) {
                    installId = null
                }

                pendingId = preferences.getString(KEY_PENDING_ID, null)
                storedId = preferences.getString(KEY_STORED_ID, null)
            }
        }

        fun storePending(installId: String?, pendingId: String) {
            this.installId = installId
            this.pendingId = pendingId

            val editor = preferences.edit()
                .putInt(KEY_DATA_VERSION, VERSION)
                .putString(KEY_PENDING_ID, pendingId)

            if (installId == null) {
                editor.remove(KEY_INSTALL_ID)
            } else {
                editor.putString(KEY_INSTALL_ID, installId)
            }

            editor.apply()
        }

        fun storeStored(installId: String, storedId: String) {
            this.installId = installId
            this.storedId = storedId

            preferences.edit()
                .putInt(KEY_DATA_VERSION, VERSION)
                .putString(KEY_INSTALL_ID, installId)
                .putString(KEY_STORED_ID, storedId)
                .apply()
        }

        fun clearStored() {
            this.installId = null
            if (this.pendingId == null) {
                this.pendingId = this.storedId
            }
            this.storedId = null

            val editor = preferences.edit()
                .putInt(KEY_DATA_VERSION, VERSION)
                .remove(KEY_INSTALL_ID)
                .remove(KEY_STORED_ID)

            if (this.pendingId == null) {
                editor.remove(KEY_PENDING_ID)
            } else {
                editor.putString(KEY_PENDING_ID, this.pendingId)
            }

            editor.apply()
        }
    }

    internal companion object {
        private const val VERSION: Int = 1
        private const val KEY_DATA_VERSION: String = "version"
        private const val KEY_INSTALL_ID: String = "install_id"
        private const val KEY_PENDING_ID: String = "pending_id"
        private const val KEY_STORED_ID: String = "stored_id"

        const val REASON_SEND: String = "send"
        const val REASON_INSTALL_ID_CHANGED: String = "installId changed"
        const val REASON_APP_START: String = "app start"
        const val REASON_RECONNECT: String = "reconnect"
    }
}
