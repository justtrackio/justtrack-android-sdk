package io.justtrack

import java.util.concurrent.TimeUnit

/**
 * Future which immediately resolves because the result is already known.
 *
 * @param <V> Type the future resolves to.
 */
internal class ValueFuture<V>(private val result: V) : AsyncFuture<V> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return false
    }

    override fun isCancelled(): Boolean {
        return false
    }

    override fun isDone(): Boolean {
        return true
    }

    override fun get(): V {
        return result
    }

    override fun get(timeout: Long, unit: TimeUnit): V {
        return result
    }

    override suspend fun await(): V {
        return result
    }

    override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): V? {
        return result
    }

    override fun registerCallback(callback: Callback<V>) {
        callback.resolve(result)
    }
}
