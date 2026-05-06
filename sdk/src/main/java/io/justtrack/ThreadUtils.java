package io.justtrack;

import android.os.Looper;

import androidx.annotation.VisibleForTesting;

class ThreadUtils {
    private static volatile long mainThreadId = -1;

    static void init() {
        if (mainThreadId == -1) {
            mainThreadId = Looper.getMainLooper().getThread().getId();
        }
    }

    @VisibleForTesting
    static void initTest() {
        mainThreadId = Thread.currentThread().getId();
    }

    static boolean isMainThread() {
        init();
        return Thread.currentThread().getId() == mainThreadId;
    }
}
