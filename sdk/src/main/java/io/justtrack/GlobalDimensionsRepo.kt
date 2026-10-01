package io.justtrack

import android.content.SharedPreferences
import io.justtrack.events.Dimension

/**
 * Persistent store for global dimensions that are automatically attached to all events.
 *
 * Global dimensions persist across sessions and app launches until explicitly cleared
 * or the app is re-installed.
 */
internal class GlobalDimensionsRepo(
    private val sharedPreferences: SharedPreferences,
) {

    /**
     * Sets a global dimension value. Pass `null` to clear the dimension.
     */
    fun set(dimension: Dimension, value: String?) {
        if (value != null) {
            sharedPreferences.putString(dimension.toString(), value)
        } else {
            sharedPreferences.remove(dimension.toString())
        }
    }

    /**
     * Returns all currently set global dimensions as a map of raw dimension keys to values.
     */
    fun getAll(): Map<String, String> {
        val all = sharedPreferences.all
        val result = mutableMapOf<String, String>()
        for ((key, value) in all) {
            if (value is String) {
                result[key] = value
            }
        }
        return result
    }

    companion object {
        const val STORE_NAME = "justtrack-global-dimensions"
    }
}
