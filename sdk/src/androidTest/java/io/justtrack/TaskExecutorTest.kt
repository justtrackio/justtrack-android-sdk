package io.justtrack

import kotlinx.coroutines.runBlocking

internal class TaskExecutorTest : TaskExecutor {
    override fun <T : Any?> executeAsFuture(task: Task<T>): AsyncFuture<T> = runBlocking {
        return@runBlocking ValueFuture(task.execute())
    }

    override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) = runBlocking {
        task.run()
    }
}
