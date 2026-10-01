package io.justtrack

import io.justtrack.executor.SerializeHandlerThread
import io.justtrack.executor.TaskExecutorImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class TaskExecutorImplTest {
    // region executeAsFuture

    @Test
    fun executeAsFuture_executesTaskAndReturnsResult() {
        runTestInThread {
            val executor = TaskExecutorImpl(ImmediateSyncExecutorService(), false, NoOpSerializeHandlerThread)

            val future = executor.executeFuture { "success" }

            assertEquals("success", future.get())
        }
    }

    @Test
    fun executeAsFuture_whenRejected_futureGetThrowsCancellationException() {
        runTestInThread {
            val executor = TaskExecutorImpl(RejectingExecutorService(), false, NoOpSerializeHandlerThread)

            val future = executor.executeFuture { "should never run" }

            assertThrows(CancellationException::class.java) { future.get() }
        }
    }

    // endregion

    // region executeAsAsyncFuture

    @Test
    fun executeFuture_executesTaskAndReturnsResult() {
        runTestInThread {
            val executor = TaskExecutorImpl(ImmediateSyncExecutorService(), false, NoOpSerializeHandlerThread)

            val result = runBlocking {
                executor.executeFuture(
                    object : Task<String> {
                        override suspend fun execute() = "success"
                    },
                ).await()
            }

            assertEquals("success", result)
        }
    }

    @Test
    fun executeFuture_whenRejected_awaitThrowsCancellationException() {
        runTestInThread {
            val executor = TaskExecutorImpl(RejectingExecutorService(), false, NoOpSerializeHandlerThread)

            val future = executor.executeFuture(
                object : Task<String> {
                    override suspend fun execute() = "should never run"
                },
            )

            assertThrows(CancellationException::class.java) { runBlocking { future.await() } }
        }
    }

    @Test
    fun executeFuture_whenRejected_futureGetThrowsCancellationException() {
        runTestInThread {
            val executor = TaskExecutorImpl(RejectingExecutorService(), false, NoOpSerializeHandlerThread)

            val future = executor.executeFuture(
                object : Task<String> {
                    override suspend fun execute() = "should never run"
                },
            )

            assertThrows(CancellationException::class.java) { future.get() }
        }
    }

    // endregion

    // region helpers

    @Throws(Throwable::class)
    private fun runTestInThread(test: Runnable) {
        ThreadUtils.initTest()
        val results: BlockingQueue<Throwable> = ArrayBlockingQueue(1)
        val sentinel: Throwable = RuntimeException()
        Thread {
            try {
                test.run()
            } catch (e: Throwable) {
                results.add(e)
                return@Thread
            }
            results.add(sentinel)
        }.start()
        val result = results.take()
        if (result !== sentinel) {
            throw result
        }
    }

    /** Executes tasks immediately on the calling thread. */
    private class ImmediateSyncExecutorService : ExecutorService by java.util.concurrent.Executors.newSingleThreadExecutor() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
        override fun <T> submit(task: java.util.concurrent.Callable<T>): Future<T> {
            val result = task.call()
            return object : Future<T> {
                override fun cancel(mayInterruptIfRunning: Boolean) = false
                override fun isCancelled() = false
                override fun isDone() = true
                override fun get() = result
                override fun get(timeout: Long, unit: TimeUnit) = result
            }
        }
    }

    /** Rejects all submitted tasks. */
    private class RejectingExecutorService : ExecutorService by java.util.concurrent.Executors.newSingleThreadExecutor() {
        override fun execute(command: Runnable) {
            throw RejectedExecutionException("executor shut down")
        }
        override fun shutdown() = Unit
        override fun isShutdown() = true
        override fun isTerminated() = true
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
        override fun <T> submit(task: java.util.concurrent.Callable<T>): Future<T> {
            throw RejectedExecutionException("executor shut down")
        }
    }

    /** A no-op SerializeHandlerThread that runs tasks immediately (no Android Looper needed). */
    private object NoOpSerializeHandlerThread : SerializeHandlerThread {
        override fun run(runnable: Runnable) = runnable.run()
    }

    // endregion
}
