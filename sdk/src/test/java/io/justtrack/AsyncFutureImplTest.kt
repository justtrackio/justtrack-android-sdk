package io.justtrack

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class AsyncFutureImplTest {
    private val taskExecutor = ImmediateSyncTaskExecutor()

    private fun <T> resolvedFuture(value: T): FutureTask<T> {
        val task = FutureTask<T> { value }
        task.run()
        return task
    }

    private fun <T> rejectedFuture(exception: Throwable): FutureTask<T> {
        val task = FutureTask<T> { throw exception }
        task.run()
        return task
    }

    @Test
    fun `get returns underlying future value`() {
        val future = AsyncFutureImpl(resolvedFuture("hello"), taskExecutor)
        Assert.assertEquals("hello", future.get())
    }

    @Test
    fun `get with timeout returns underlying future value`() {
        val future = AsyncFutureImpl(resolvedFuture("hello"), taskExecutor)
        Assert.assertEquals("hello", future.get(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `get with timeout throws TimeoutException when not completed in time`() {
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task: Future<String> = executor.submit<String> {
                latch.await()
                "done"
            }
            val future = AsyncFutureImpl(task, taskExecutor)
            try {
                future.get(50, TimeUnit.MILLISECONDS)
                Assert.fail("should have thrown TimeoutException")
            } catch (e: TimeoutException) {
                Assert.assertNotNull(e)
            }
        } finally {
            latch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `isDone reflects underlying future`() {
        val future = AsyncFutureImpl(resolvedFuture("hi"), taskExecutor)
        Assert.assertTrue(future.isDone())
    }

    @Test
    fun `isDone returns false when underlying future not done`() {
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task: Future<String> = executor.submit<String> {
                latch.await()
                "done"
            }
            val future = AsyncFutureImpl(task, taskExecutor)
            Assert.assertFalse(future.isDone())
        } finally {
            latch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancel delegates to underlying future`() {
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task: Future<String> = executor.submit<String> {
                latch.await()
                "done"
            }
            val future = AsyncFutureImpl(task, taskExecutor)
            Assert.assertTrue(future.cancel(true))
            Assert.assertTrue(future.isCancelled())
        } finally {
            latch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancel returns false on already completed future`() {
        val future = AsyncFutureImpl(resolvedFuture("hi"), taskExecutor)
        Assert.assertFalse(future.cancel(true))
        Assert.assertFalse(future.isCancelled())
    }

    @Test
    fun `await returns underlying value`() {
        val future = AsyncFutureImpl(resolvedFuture("hello"), taskExecutor)
        runBlocking {
            Assert.assertEquals("hello", future.await())
        }
    }

    @Test(expected = ExecutionException::class)
    fun `await throws when future rejects`() {
        val future = AsyncFutureImpl<String>(rejectedFuture(RuntimeException("boom")), taskExecutor)
        runBlocking {
            future.await()
        }
    }

    @Test
    fun `awaitOrNull returns underlying value`() {
        val future = AsyncFutureImpl(resolvedFuture("hello"), taskExecutor)
        runBlocking {
            Assert.assertEquals("hello", future.awaitOrNull(100, TimeUnit.MILLISECONDS))
        }
    }

    @Test
    fun `awaitOrNull returns null on timeout`() {
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task: Future<String> = executor.submit<String> {
                latch.await()
                "done"
            }
            val future = AsyncFutureImpl(task, taskExecutor)
            runBlocking {
                Assert.assertNull(future.awaitOrNull(50, TimeUnit.MILLISECONDS))
            }
        } finally {
            latch.countDown()
            executor.shutdownNow()
        }
    }

    @Test(expected = ExecutionException::class)
    fun `awaitOrNull rethrows underlying execution exception`() {
        val future = AsyncFutureImpl<String>(rejectedFuture(RuntimeException("boom")), taskExecutor)
        runBlocking {
            future.awaitOrNull(100, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun `registerCallback resolves with future value`() {
        val future = AsyncFutureImpl(resolvedFuture("hello"), taskExecutor)
        val resolved = AtomicReference<String>()
        val rejected = AtomicReference<Throwable>()
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                resolved.set(response)
            }

            override fun reject(exception: Throwable) {
                rejected.set(exception)
            }
        })
        Assert.assertEquals("hello", resolved.get())
        Assert.assertNull(rejected.get())
    }

    @Test
    fun `registerCallback rejects with cause of ExecutionException`() {
        val cause = IllegalStateException("bad state")
        val future = AsyncFutureImpl<String>(rejectedFuture(cause), taskExecutor)
        val resolved = AtomicReference<String>()
        val rejected = AtomicReference<Throwable>()
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                resolved.set(response)
            }

            override fun reject(exception: Throwable) {
                rejected.set(exception)
            }
        })
        Assert.assertNull(resolved.get())
        Assert.assertSame(cause, rejected.get())
    }

    @Test
    fun `registerCallback rejects with non-execution Throwable`() {
        // A Future whose get() throws InterruptedException (not ExecutionException)
        // simulated via a cancelled future, which throws CancellationException from get().
        val latch = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task: Future<String> = executor.submit<String> {
                latch.await()
                "done"
            }
            task.cancel(true)
            val future = AsyncFutureImpl(task, taskExecutor)
            val rejected = AtomicReference<Throwable>()
            future.registerCallback(object : Callback<String> {
                override fun resolve(response: String) {
                    Assert.fail("should not resolve")
                }

                override fun reject(exception: Throwable) {
                    rejected.set(exception)
                }
            })
            Assert.assertNotNull(rejected.get())
        } finally {
            latch.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `registerCallback rejects callback when executor rejects the task`() {
        val rejection = java.util.concurrent.RejectedExecutionException("pool shutdown")
        val rejectingExecutor = object : io.justtrack.executor.TaskExecutor {
            override fun <V> executeFuture(task: Task<V>): AsyncFuture<V> {
                throw UnsupportedOperationException()
            }

            override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler) {
                rejectedHandler.handleRejectedExecution(rejection)
            }

            override fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean) {
                rejectedHandler.handleRejectedExecution(rejection)
            }

            override fun <V> wrap(callback: Callback<V>): Callback<V> = callback
        }

        val future = AsyncFutureImpl(resolvedFuture("hello"), rejectingExecutor)
        val resolved = AtomicReference<String>()
        val rejected = AtomicReference<Throwable>()
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                resolved.set(response)
            }

            override fun reject(exception: Throwable) {
                rejected.set(exception)
            }
        })
        Assert.assertNull(resolved.get())
        Assert.assertSame(rejection, rejected.get())
    }

    @Test
    fun `registerCallback only resolves once`() {
        val future = AsyncFutureImpl(resolvedFuture("hello"), taskExecutor)
        var resolveCount = 0
        var rejectCount = 0
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                resolveCount++
            }

            override fun reject(exception: Throwable) {
                rejectCount++
            }
        })
        Assert.assertEquals(1, resolveCount)
        Assert.assertEquals(0, rejectCount)
    }
}
