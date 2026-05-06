package io.justtrack

import kotlinx.coroutines.delay
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TaskFutureTest {
    @Test
    @Throws(Throwable::class)
    fun simpleTest() {
        runTestInThread {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        return "success"
                    }
                },
            )
            task.run()
            Assert.assertEquals("success", task.get())
        }
    }

    @Test
    @Throws(Throwable::class)
    fun throwsOnMainThread() {
        ThreadUtils.initTest()
        try {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        return "success"
                    }
                },
            )
            task.run()
            task.get()
            Assert.fail("should never reach here")
        } catch (e: RuntimeException) {
            Assert.assertEquals("Must not be called on the main application thread", e.message)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun executeThrows() {
        runTestInThread {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        throw TestException()
                    }
                },
            )
            task.execute()
            assertThrow(task)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun delayedTest() {
        runTestInThread {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        delay(500)

                        return "success"
                    }
                },
            )
            // start execution
            task.execute()
            try {
                task.get(100, TimeUnit.MILLISECONDS)
                Assert.fail("Should not have reached here")
            } catch (e: TimeoutException) {
                // successful got the timeout
            }
            Assert.assertEquals("success", task.get())
        }
    }

    @Test
    @Throws(Throwable::class)
    fun delayedReject() {
        runTestInThread {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        delay(500)

                        throw TestException()
                    }
                },
            )
            // start execution
            task.execute()
            try {
                task.get(100, TimeUnit.MILLISECONDS)
                Assert.fail("Should not have reached here")
            } catch (e: TimeoutException) {
                // successful got the timeout
            }
            assertThrow(task)
        }
    }

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

    private fun assertThrow(runnable: RunnableFuture<String>) {
        try {
            runnable.get()
        } catch (exception: ExecutionException) {
            if (exception.cause is TestException) {
                return
            } else {
                throw AssertionError("unexpected exception was thrown", exception)
            }
        }

        throw AssertionError("no TestException was thrown")
    }

    private class TestException : RuntimeException()
}
