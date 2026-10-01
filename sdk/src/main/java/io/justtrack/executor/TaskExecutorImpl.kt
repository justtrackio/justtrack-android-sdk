package io.justtrack.executor

import io.justtrack.AsyncFuture
import io.justtrack.AsyncFutureImpl
import io.justtrack.Callback
import io.justtrack.RejectedExecutionExceptionHandler
import io.justtrack.Task
import io.justtrack.TaskFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

internal class TaskExecutorImpl(
    private val executor: ExecutorService,
    private val runCallbackSerially: Boolean,
    private val serializeHandlerThread: SerializeHandlerThread,
) : TaskExecutor {
    override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
        val future = TaskFuture(task)
        execute(future) { future.cancel(false) }
        return build(future)
    }

    override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
        execute(task, rejectedHandler, true)
    }

    override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
        try {
            if (runCallbackSerially && !ignoreSerially) {
                serializeHandlerThread.run(task)
            } else {
                executor.execute(task)
            }
        } catch (exception: RejectedExecutionException) {
            rejectedHandler.handleRejectedExecution(exception)
        }
    }

    private fun <V> build(future: Future<V>): AsyncFuture<V> {
        return AsyncFutureImpl(future, this)
    }

    override fun <V> wrap(callback: Callback<V>): Callback<V> {
        return object : Callback<V> {
            override fun resolve(response: V) {
                execute({ callback.resolve(response) }, { exception -> callback.reject(exception) }, false)
            }

            override fun reject(exception: Throwable) {
                execute({ callback.reject(exception) }, { callback.reject(it) }, false)
            }
        }
    }
}
