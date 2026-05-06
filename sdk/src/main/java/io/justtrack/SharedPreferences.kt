package io.justtrack

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal fun Context.getSharePrefIO(name: String, mode: Int): SharedPreferences = runBlocking {
    withContext(Dispatchers.IO) {
        getSharedPreferences(
            name,
            mode,
        )
    }
}

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

internal fun <T> SharedPreferences.withIO(action: SharedPreferences.() -> T): T = runBlocking {
    withContext(Dispatchers.IO) {
        action.invoke(this@withIO)
    }
}

internal fun SharedPreferences.getStringIO(key: String, default: String?): String? = runBlocking {
    withIO {
        getString(key, default)
    }
}

internal fun SharedPreferences.getIntIO(key: String, default: Int): Int = runBlocking {
    withIO {
        getInt(key, default)
    }
}

internal fun SharedPreferences.getLongIO(key: String, default: Long): Long = runBlocking {
    withIO {
        getLong(key, default)
    }
}

internal fun SharedPreferences.putStringIO(key: String, value: String?) = runBlocking {
    withIO {
        edit().putString(key, value).apply()
    }
}

internal fun SharedPreferences.putIntIO(key: String, value: Int) = runBlocking {
    withIO {
        edit().putInt(key, value).apply()
    }
}

internal fun SharedPreferences.removeIO(key: String) = runBlocking {
    withIO {
        edit().remove(key).apply()
    }
}

internal fun SharedPreferences.clearIO() = runBlocking {
    withIO {
        edit().clear().apply()
    }
}
