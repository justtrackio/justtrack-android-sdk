package io.justtrack

import android.os.Looper
import androidx.annotation.VisibleForTesting
import kotlin.concurrent.Volatile

internal object ThreadUtils {
    @Volatile
    private var mainThreadId: Long = -1

    fun init() {
        if (mainThreadId == -1L) {
            mainThreadId = Looper.getMainLooper().thread.id
        }
    }

    @VisibleForTesting
    fun initTest() {
        mainThreadId = Thread.currentThread().id
    }

    val isMainThread: Boolean
        get() {
            init()
            return Thread.currentThread().id == mainThreadId
        }
}
