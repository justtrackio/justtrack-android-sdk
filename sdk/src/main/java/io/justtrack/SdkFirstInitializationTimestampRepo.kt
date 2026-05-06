package io.justtrack

import android.content.SharedPreferences

internal class SdkFirstInitializationTimestampRepo(
    private val sharedPreferences: SharedPreferences,
) {

    fun getOrCreate(): Long {
        val storedValue = getStoredValue()
        if (storedValue != null) {
            return storedValue
        } else {
            val currentTime = System.currentTimeMillis()
            storeValue(currentTime)
            return currentTime
        }
    }

    private fun getStoredValue(): Long? {
        val firstInitializeTime = sharedPreferences.getLong(KEY_FIRST_INITIALIZE_TIME, -1)
        return if (firstInitializeTime == -1L) {
            null
        } else {
            firstInitializeTime
        }
    }

    private fun storeValue(value: Long) {
        sharedPreferences.edit().putLong(KEY_FIRST_INITIALIZE_TIME, value).apply()
    }

    companion object {
        const val STORE_NAME = "justtrack-timestamp"
        private const val KEY_FIRST_INITIALIZE_TIME = "first_initialize_time"
    }
}
