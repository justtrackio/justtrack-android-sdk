package io.justtrack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class AsyncFutureImpl<T>(
    private val future: Future<T>,
    private val taskExecutor: TaskExecutor,
    private val callbackInvoker: CallbackInvoker,
) : AsyncFuture<T> {
    override suspend fun await(): T {
        return withContext(Dispatchers.IO) {
            get()
        }
    }

    override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): T? {
        return withContext(Dispatchers.IO) {
            try {
                get(timeout, timeUnit)
            } catch (e: TimeoutException) {
                null
            }
        }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return future.cancel(mayInterruptIfRunning)
    }

    override fun isCancelled(): Boolean {
        return future.isCancelled
    }

    override fun isDone(): Boolean {
        return future.isDone
    }

    override fun get(): T {
        return future.get()
    }

    override fun get(timeout: Long, unit: TimeUnit?): T {
        return future.get(timeout, unit)
    }

    override fun registerCallback(callback: Callback<T>) {
        val wrappedCallback: Callback<T> = callbackInvoker.wrap(callback)
        taskExecutor.execute(
            {
                // wrap the callback again to ensure we only resolve/reject it once
                val onceCallback: Callback<T> = CallbackImpl<T, Any>(wrappedCallback)
                try {
                    onceCallback.resolve(future.get())
                } catch (exception: ExecutionException) {
                    val cause = exception.cause
                    onceCallback.reject(cause ?: exception)
                } catch (exception: Throwable) {
                    onceCallback.reject(exception)
                }
            },
            { exception: RejectedExecutionException? -> wrappedCallback.reject(exception!!) },
        )
    }
}
