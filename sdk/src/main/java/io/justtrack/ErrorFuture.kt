package io.justtrack

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Future which immediately fails because the error is already known.
 *
 * @param <V> Type the future would resolve to if it would ever succeed.
 */
internal class ErrorFuture<V>(error: Throwable) : AsyncFuture<V> {
    private val error: ExecutionException

    init {
        this.error = ExecutionException(error)
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

    @Throws(ExecutionException::class)
    override fun get(): V {
        throw error
    }

    @Throws(ExecutionException::class)
    override fun get(timeout: Long, unit: TimeUnit): V {
        throw error
    }

    @Throws(ExecutionException::class)
    override suspend fun await(): V {
        throw error
    }

    @Throws(ExecutionException::class)
    override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): V? {
        throw error
    }

    override fun registerCallback(callback: Callback<V>) {
        callback.reject(error)
    }
}
