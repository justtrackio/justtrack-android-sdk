package io.justtrack

import java.util.concurrent.TimeUnit

internal class TestAsyncFuture<T>(private val returnData: T) : AsyncFuture<T> {
    override suspend fun await(): T {
        return returnData
    }

    override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): T? {
        return returnData
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return false
    }

    override fun isCancelled(): Boolean {
        return false
    }

    override fun isDone(): Boolean {
        return true
    }

    override fun get(): T {
        return returnData
    }

    override fun get(timeout: Long, unit: TimeUnit?): T {
        return returnData
    }

    override fun registerCallback(callback: Callback<T>) {
        callback.resolve(returnData)
    }
}
