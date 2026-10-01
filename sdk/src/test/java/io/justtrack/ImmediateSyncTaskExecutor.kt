package io.justtrack

import io.justtrack.executor.TaskExecutor
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [TaskExecutor] for unit tests that executes tasks synchronously on the calling thread.
 * The result is immediately available via the returned [Future] or [AsyncFuture] without
 * spawning any threads or touching the Android [android.os.Looper].
 *
 * [executeCount] can be used to assert how many tasks were submitted during a test.
 */
internal class ImmediateSyncTaskExecutor : TaskExecutor {
    val executeCount = AtomicInteger(0)

    override fun <T> executeFuture(task: Task<T>): AsyncFuture<T> {
        executeCount.incrementAndGet()
        val value = runBlocking { task.execute() }
        return AsyncFutureImpl(ValueFuture(value), this)
    }

    override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
        task.run()
    }

    override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
        task.run()
    }

    override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
}
