package io.justtrack.executor

import io.justtrack.AsyncFuture
import io.justtrack.Callback
import io.justtrack.RejectedExecutionExceptionHandler
import io.justtrack.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class TaskExecutorImplTest {
    /** Executor that simply runs every submitted runnable inline on the calling thread. */
    private class InlineExecutorService : java.util.concurrent.AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = true
    }

    /** Executor that always throws RejectedExecutionException to exercise rejection branches. */
    private class RejectingExecutorService : java.util.concurrent.AbstractExecutorService() {
        override fun execute(command: Runnable): Unit = throw RejectedExecutionException("rejected")
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = true
    }

    /** Inline SerializeHandlerThread that runs the runnable synchronously. */
    private class InlineSerializeHandlerThread : SerializeHandlerThread {
        var invocations = 0
        override fun run(runnable: Runnable) {
            invocations++
            runnable.run()
        }
    }

    /** SerializeHandlerThread that throws RejectedExecutionException. */
    private class RejectingSerializeHandlerThread : SerializeHandlerThread {
        override fun run(runnable: Runnable): Unit = throw RejectedExecutionException("rejected by handler")
    }

    private fun newExecutorImpl(
        executor: ExecutorService = InlineExecutorService(),
        runCallbackSerially: Boolean = false,
        serializeHandlerThread: SerializeHandlerThread = InlineSerializeHandlerThread(),
    ) = TaskExecutorImpl(executor, runCallbackSerially, serializeHandlerThread)

    // ---------- executeFuture ----------

    @Test
    fun `executeFuture runs task and registerCallback receives resolved value`() {
        // Use a real background executor so TaskFuture's checkNotMainThread doesn't trip.
        val realExecutor = Executors.newSingleThreadExecutor()
        try {
            val impl = newExecutorImpl(executor = realExecutor, runCallbackSerially = false)
            val task = Task<String> { "hello" }

            val future: AsyncFuture<String> = impl.executeFuture(task)

            assertNotNull(future)
            val resolved = AtomicReference<String>()
            val rejected = AtomicReference<Throwable>()
            val callbackCalled = AtomicInteger(0)
            val latch = java.util.concurrent.CountDownLatch(1)
            future.registerCallback(object : Callback<String> {
                override fun resolve(response: String) {
                    callbackCalled.incrementAndGet()
                    resolved.set(response)
                    latch.countDown()
                }
                override fun reject(exception: Throwable) {
                    callbackCalled.incrementAndGet()
                    rejected.set(exception)
                    latch.countDown()
                }
            })

            assertTrue("callback must fire", latch.await(2, TimeUnit.SECONDS))
            assertEquals("callback.resolve must fire exactly once", 1, callbackCalled.get())
            assertEquals("hello", resolved.get())
            assertNull(rejected.get())
        } finally {
            realExecutor.shutdownNow()
        }
    }

    @Test
    fun `executeFuture rejects callback when task throws`() {
        val realExecutor = Executors.newSingleThreadExecutor()
        try {
            val impl = newExecutorImpl(executor = realExecutor, runCallbackSerially = false)
            val boom = IllegalStateException("nope")
            val task = Task<String> { throw boom }

            val future = impl.executeFuture(task)

            val rejected = AtomicReference<Throwable>()
            val callbackCalled = AtomicInteger(0)
            val latch = java.util.concurrent.CountDownLatch(1)
            future.registerCallback(object : Callback<String> {
                override fun resolve(response: String) {
                    callbackCalled.incrementAndGet()
                    latch.countDown()
                }
                override fun reject(exception: Throwable) {
                    callbackCalled.incrementAndGet()
                    rejected.set(exception)
                    latch.countDown()
                }
            })

            assertTrue("callback must fire", latch.await(2, TimeUnit.SECONDS))
            assertEquals(1, callbackCalled.get())
            assertSame(boom, rejected.get())
        } finally {
            realExecutor.shutdownNow()
        }
    }

    // ---------- execute(Runnable, RejectedExecutionExceptionHandler) ----------

    @Test
    fun `execute two-arg delegates to direct executor and runs runnable`() {
        val executor = InlineExecutorService()
        val handlerThread = InlineSerializeHandlerThread()
        val impl = newExecutorImpl(executor, runCallbackSerially = true, serializeHandlerThread = handlerThread)
        val ran = AtomicBoolean(false)
        val rejected = AtomicBoolean(false)

        // ignoreSerially defaults to true => skips serializeHandlerThread, uses executor.
        impl.execute({ ran.set(true) }, { rejected.set(true) })

        assertTrue("runnable should have been called", ran.get())
        assertEquals("handler thread must NOT be used when ignoreSerially=true", 0, handlerThread.invocations)
        assertTrue("reject handler should not be called on success path", !rejected.get())
    }

    // ---------- execute(Runnable, ..., ignoreSerially) ----------

    @Test
    fun `execute uses serializeHandlerThread when runCallbackSerially and not ignoredSerially`() {
        val executor = InlineExecutorService()
        val handlerThread = InlineSerializeHandlerThread()
        val impl = newExecutorImpl(executor, runCallbackSerially = true, serializeHandlerThread = handlerThread)
        val ran = AtomicBoolean(false)

        impl.execute({ ran.set(true) }, { fail("not expected") }, false)

        assertEquals(1, handlerThread.invocations)
        assertTrue("runnable should be invoked through handler thread", ran.get())
    }

    @Test
    fun `execute uses raw executor when runCallbackSerially is false`() {
        val executor = InlineExecutorService()
        val handlerThread = InlineSerializeHandlerThread()
        val impl = newExecutorImpl(executor, runCallbackSerially = false, serializeHandlerThread = handlerThread)
        val ran = AtomicBoolean(false)

        impl.execute({ ran.set(true) }, { fail("not expected") }, false)

        assertEquals(0, handlerThread.invocations)
        assertTrue(ran.get())
    }

    @Test
    fun `execute uses raw executor when ignoreSerially is true even with runCallbackSerially`() {
        val executor = InlineExecutorService()
        val handlerThread = InlineSerializeHandlerThread()
        val impl = newExecutorImpl(executor, runCallbackSerially = true, serializeHandlerThread = handlerThread)
        val ran = AtomicBoolean(false)

        impl.execute({ ran.set(true) }, { fail("not expected") }, true)

        assertEquals(0, handlerThread.invocations)
        assertTrue(ran.get())
    }

    @Test
    fun `execute invokes rejectedHandler when executor throws RejectedExecutionException`() {
        val impl = newExecutorImpl(executor = RejectingExecutorService(), runCallbackSerially = false)
        val caught = AtomicReference<RejectedExecutionException>()

        impl.execute({ fail("must not run") }, { caught.set(it) }, false)

        assertNotNull("reject handler must be called", caught.get())
        assertEquals("rejected", caught.get().message)
    }

    @Test
    fun `execute invokes rejectedHandler when serialize handler throws RejectedExecutionException`() {
        val impl = newExecutorImpl(
            runCallbackSerially = true,
            serializeHandlerThread = RejectingSerializeHandlerThread(),
        )
        val caught = AtomicReference<RejectedExecutionException>()

        impl.execute({ fail("must not run") }, { caught.set(it) }, false)

        assertNotNull(caught.get())
        assertEquals("rejected by handler", caught.get().message)
    }

    // ---------- wrap(Callback) ----------

    @Test
    fun `wrap resolve runs callback resolve on executor and callback is called once`() {
        val impl = newExecutorImpl(runCallbackSerially = false)
        val callbackCalled = AtomicInteger(0)
        val received = AtomicReference<String>()
        val callback = object : Callback<String> {
            override fun resolve(response: String) {
                callbackCalled.incrementAndGet()
                received.set(response)
            }
            override fun reject(exception: Throwable) = fail("reject not expected")
        }

        val wrapped = impl.wrap(callback)
        wrapped.resolve("payload")

        assertEquals("callback.resolve must be invoked exactly once", 1, callbackCalled.get())
        assertEquals("payload", received.get())
    }

    @Test
    fun `wrap reject runs callback reject on executor and callback is called once`() {
        val impl = newExecutorImpl(runCallbackSerially = false)
        val callbackCalled = AtomicInteger(0)
        val received = AtomicReference<Throwable>()
        val callback = object : Callback<String> {
            override fun resolve(response: String) = fail("resolve not expected")
            override fun reject(exception: Throwable) {
                callbackCalled.incrementAndGet()
                received.set(exception)
            }
        }
        val error = IllegalStateException("boom")

        val wrapped = impl.wrap(callback)
        wrapped.reject(error)

        assertEquals(1, callbackCalled.get())
        assertSame(error, received.get())
    }

    @Test
    fun `wrap resolve calls callback reject when executor rejects`() {
        val impl = newExecutorImpl(executor = RejectingExecutorService(), runCallbackSerially = false)
        val resolveCalled = AtomicInteger(0)
        val rejectCalled = AtomicInteger(0)
        val rejectedWith = AtomicReference<Throwable>()
        val callback = object : Callback<String> {
            override fun resolve(response: String) {
                resolveCalled.incrementAndGet()
            }
            override fun reject(exception: Throwable) {
                rejectCalled.incrementAndGet()
                rejectedWith.set(exception)
            }
        }

        val wrapped = impl.wrap(callback)
        wrapped.resolve("payload")

        assertEquals("inner resolve must NOT run because executor rejected", 0, resolveCalled.get())
        assertEquals("callback.reject must be invoked exactly once via rejectedHandler", 1, rejectCalled.get())
        assertTrue(rejectedWith.get() is RejectedExecutionException)
    }

    @Test
    fun `wrap reject calls callback reject via rejectedHandler when executor rejects`() {
        val impl = newExecutorImpl(executor = RejectingExecutorService(), runCallbackSerially = false)
        val rejectCalled = AtomicInteger(0)
        val rejectedWith = AtomicReference<Throwable>()
        val callback = object : Callback<String> {
            override fun resolve(response: String) = fail("resolve not expected")
            override fun reject(exception: Throwable) {
                rejectCalled.incrementAndGet()
                rejectedWith.set(exception)
            }
        }
        val originalError = IllegalStateException("original")

        val wrapped = impl.wrap(callback)
        wrapped.reject(originalError)

        // First the executor rejects, so the wrap's reject's rejectedHandler is invoked which
        // calls callback.reject(RejectedExecutionException). The original error is dropped.
        assertEquals(1, rejectCalled.get())
        assertTrue(rejectedWith.get() is RejectedExecutionException)
    }

    // ---------- wrap routes through serializeHandlerThread (runCallbackSerially=true, ignoreSerially=false) ----------

    @Test
    fun `wrap resolve goes through serializeHandlerThread when runCallbackSerially is true`() {
        val handlerThread = InlineSerializeHandlerThread()
        val impl = newExecutorImpl(runCallbackSerially = true, serializeHandlerThread = handlerThread)
        val callbackCalled = AtomicInteger(0)
        val callback = object : Callback<String> {
            override fun resolve(response: String) {
                callbackCalled.incrementAndGet()
            }
            override fun reject(exception: Throwable) = fail("reject not expected")
        }

        impl.wrap(callback).resolve("x")

        assertEquals("wrap must pass ignoreSerially=false so handler thread is used", 1, handlerThread.invocations)
        assertEquals(1, callbackCalled.get())
    }

    @Test
    fun `wrap reject goes through serializeHandlerThread when runCallbackSerially is true`() {
        val handlerThread = InlineSerializeHandlerThread()
        val impl = newExecutorImpl(runCallbackSerially = true, serializeHandlerThread = handlerThread)
        val callbackCalled = AtomicInteger(0)
        val callback = object : Callback<String> {
            override fun resolve(response: String) = fail("resolve not expected")
            override fun reject(exception: Throwable) {
                callbackCalled.incrementAndGet()
            }
        }

        impl.wrap(callback).reject(IllegalStateException("e"))

        assertEquals(1, handlerThread.invocations)
        assertEquals(1, callbackCalled.get())
    }

    // ---------- executeFuture forwards cancellation via rejectedHandler ----------

    @Test
    fun `executeFuture cancels the future when executor rejects and callback receives rejection`() {
        // The executor.execute throws RejectedExecutionException; the inline rejectedHandler
        // calls future.cancel(false). Verify the returned future is done and the registered
        // callback is invoked with a Throwable.
        val rejectingExecutor = mock<ExecutorService>()
        whenever(rejectingExecutor.execute(any())).thenThrow(RejectedExecutionException("nope"))
        val impl = newExecutorImpl(executor = rejectingExecutor, runCallbackSerially = false)

        val future = impl.executeFuture(Task<String> { "v" })

        assertTrue("future should be done (cancelled) when executor rejects", future.isDone)

        val callbackCalled = AtomicInteger(0)
        val rejected = AtomicReference<Throwable>()
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                callbackCalled.incrementAndGet()
            }
            override fun reject(exception: Throwable) {
                callbackCalled.incrementAndGet()
                rejected.set(exception)
            }
        })

        assertEquals("callback must fire exactly once on the cancelled future", 1, callbackCalled.get())
        assertNotNull("callback must be rejected (cancellation)", rejected.get())
    }

    // ---------- Sanity: a no-op rejectedHandler is never called on success ----------

    @Test
    fun `rejectedHandler is never invoked on successful direct execute`() {
        val impl = newExecutorImpl(runCallbackSerially = false)
        val handler: RejectedExecutionExceptionHandler = mock()
        impl.execute({ /* no-op */ }, handler)
        verifyNoInteractions(handler)
    }
}
