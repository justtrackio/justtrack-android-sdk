package io.justtrack

import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Extends the Future interface from Java with methods to await the returned value in a coroutine or
 * register a callback to be called once the future resolves.
 */
interface AsyncFuture<T> : Future<T> {
    /**
     * Wait for the future to resolve to the result value and suspend the current coroutine until then.
     * If the future resolves to an exception, this method rethrows the exception.
     */
    @Throws(ExecutionException::class)
    suspend fun await(): T

    /**
     * Like [await], but returns null if the timeout is reached.
     */
    @Throws(ExecutionException::class)
    suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): T?

    /**
     * Await a future and resolve or reject the callback as soon as the future resolves.
     * Using this method you can easily turn synchronously awaiting a future into a callback.
     *
     */
    fun registerCallback(callback: Callback<T>)
}
