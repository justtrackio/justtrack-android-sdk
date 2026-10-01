package io.justtrack

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal fun <T> Context.getSharePrefWithIO(name: String, mode: Int, action: SharedPreferences.() -> T): T = runBlocking {
    withContext(Dispatchers.IO) {
        action.invoke(
            getSharedPreferences(
                name,
                mode,
            ),
        )
    }
}

internal fun SharedPreferences.putString(key: String, value: String?) = edit().putString(key, value).apply()

internal fun SharedPreferences.putInt(key: String, value: Int) = edit().putInt(key, value).apply()

internal fun SharedPreferences.remove(key: String) = edit().remove(key).apply()

internal fun SharedPreferences.clear() = edit().clear().apply()
