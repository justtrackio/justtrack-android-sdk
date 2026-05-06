package io.justtrack

internal interface TaskExecutor {
    fun <T> executeAsFuture(task: Task<T>): AsyncFuture<T>

    fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler)
}
