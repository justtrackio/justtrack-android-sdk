package io.justtrack

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TaskFutureTest {
    @Test
    @Throws(Throwable::class)
    fun simpleTest() {
        runTestInCoroutine {
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
        runTestInCoroutine {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        throw TestException()
                    }
                },
            )
            task.run()
            assertThrow(task)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun delayedTest() {
        runTestInCoroutine {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        delay(500)

                        return "success"
                    }
                },
            )
            // start execution in a separate coroutine so get() can time out
            launch(Dispatchers.IO) { task.run() }
            try {
                task.get(100, TimeUnit.MILLISECONDS)
                Assert.fail("Should not have reached here")
            } catch (e: TimeoutException) {
                // successfully got the timeout
            }
            Assert.assertEquals("success", task.get())
        }
    }

    @Test
    @Throws(Throwable::class)
    fun delayedReject() {
        runTestInCoroutine {
            val task = TaskFuture(
                object : Task<String> {
                    override suspend fun execute(): String {
                        delay(500)

                        throw TestException()
                    }
                },
            )
            // start execution in a separate coroutine so get() can time out
            launch(Dispatchers.IO) { task.run() }
            try {
                task.get(100, TimeUnit.MILLISECONDS)
                Assert.fail("Should not have reached here")
            } catch (e: TimeoutException) {
                // successfully got the timeout
            }
            assertThrow(task)
        }
    }

    @Throws(Throwable::class)
    private fun runTestInCoroutine(test: suspend CoroutineScope.() -> Unit) {
        ThreadUtils.initTest()
        val results = java.util.concurrent.ArrayBlockingQueue<Throwable>(1)
        val sentinel: Throwable = RuntimeException()
        Thread {
            try {
                runBlocking(block = test)
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
