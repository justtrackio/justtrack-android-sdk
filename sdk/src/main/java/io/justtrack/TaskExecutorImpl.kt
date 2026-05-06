package io.justtrack

import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

internal class TaskExecutorImpl constructor(
    val sdk: BaseJustTrackSdk,
    val executor: ExecutorService,
    val callbackInvoker: CallbackInvoker,
) : TaskExecutor {
    override fun <T> executeAsFuture(task: Task<T>): AsyncFuture<T> {
        val future = TaskFuture(task)
        future.execute()

        return AsyncFutureImpl(future, this, callbackInvoker)
    }

    override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
        try {
            executor.execute(task)
        } catch (exception: RejectedExecutionException) {
            rejectedHandler.handleRejectedExecution(exception)
        }
    }
}
