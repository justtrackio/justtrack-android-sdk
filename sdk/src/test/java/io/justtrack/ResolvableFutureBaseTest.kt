package io.justtrack

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class ResolvableFutureBaseTest {

    @Test
    fun isDone_returnsFalseWhilePending() {
        val future = ResolvableFuture<String>()

        assertFalse(future.isDone)
    }

    @Test
    fun isCancelled_alwaysFalse() {
        val future = ResolvableFuture<String>()

        assertFalse(future.isCancelled)
    }

    @Test
    fun cancel_alwaysReturnsFalse() {
        val future = ResolvableFuture<String>()

        assertFalse(future.cancel(true))
        assertFalse(future.cancel(false))
        assertFalse(future.isCancelled)
    }

    @Test
    fun resolve_setsResultAndMarksDone() {
        val future = ResolvableFuture<String>()

        future.resolve("ok")

        assertTrue(future.isDone)
        assertEquals("ok", future.get())
    }

    @Test
    fun resolve_isIgnoredAfterFirstResolution() {
        val future = ResolvableFuture<String>()
        future.resolve("first")

        future.resolve("second")

        assertEquals("first", future.get())
    }

    @Test
    fun resolve_isIgnoredAfterRejection() {
        val future = ResolvableFuture<String>()
        future.reject(IllegalStateException("boom"))

        future.resolve("late")

        val ex = assertThrows(ExecutionException::class.java) { future.get() }
        assertTrue(ex.cause is IllegalStateException)
    }

    @Test
    fun reject_setsErrorAndMarksDone() {
        val future = ResolvableFuture<String>()
        val cause = IllegalStateException("boom")

        future.reject(cause)

        assertTrue(future.isDone)
        val ex = assertThrows(ExecutionException::class.java) { future.get() }
        assertSame(cause, ex.cause)
    }

    @Test
    fun reject_isIgnoredAfterFirstRejection() {
        val future = ResolvableFuture<String>()
        val firstCause = IllegalStateException("first")
        future.reject(firstCause)

        future.reject(IllegalStateException("second"))

        val ex = assertThrows(ExecutionException::class.java) { future.get() }
        assertSame(firstCause, ex.cause)
    }

    @Test
    fun getWithTimeout_returnsValueAlreadyAvailable() {
        val future = ResolvableFuture<String>()
        future.resolve("done")

        assertEquals("done", future.get(1, TimeUnit.SECONDS))
    }

    @Test
    fun getWithTimeout_throwsExecutionExceptionForRejectedFuture() {
        val future = ResolvableFuture<String>()
        future.reject(IllegalStateException("boom"))

        val ex = assertThrows(ExecutionException::class.java) { future.get(1, TimeUnit.SECONDS) }
        assertTrue(ex.cause is IllegalStateException)
    }

    @Test
    fun getWithTimeout_throwsTimeoutWhenNeverResolved() {
        val future = ResolvableFuture<String>()

        assertThrows(TimeoutException::class.java) { future.get(10, TimeUnit.MILLISECONDS) }
    }

    @Test
    fun getWithTimeout_unblocksWhenResolvedByAnotherThread() {
        val future = ResolvableFuture<String>()
        val resolver = Thread {
            Thread.sleep(50)
            future.resolve("late-ok")
        }
        resolver.start()

        assertEquals("late-ok", future.get(2, TimeUnit.SECONDS))
        resolver.join()
    }

    @Test
    fun get_blocksUntilResolved() {
        val future = ResolvableFuture<String>()
        val received = AtomicReference<String?>(null)
        val started = CountDownLatch(1)
        val getter = Thread {
            started.countDown()
            received.set(future.get())
        }
        getter.start()
        started.await()
        Thread.sleep(20)
        future.resolve("blocked-ok")
        getter.join(2000)

        assertEquals("blocked-ok", received.get())
    }

    @Test
    fun registerCallback_resolvesImmediatelyWhenAlreadyResolved() {
        val future = ResolvableFuture<String>()
        future.resolve("ok")
        val received = AtomicReference<String?>(null)

        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                received.set(response)
            }
            override fun reject(exception: Throwable) {
                fail("unexpected")
            }
        })

        assertEquals("ok", received.get())
    }

    @Test
    fun registerCallback_rejectsImmediatelyWhenAlreadyRejected() {
        val future = ResolvableFuture<String>()
        val cause = IllegalStateException("boom")
        future.reject(cause)
        val received = AtomicReference<Throwable?>(null)

        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                fail("unexpected resolve $response")
            }
            override fun reject(exception: Throwable) {
                received.set(exception)
            }
        })

        val ex = received.get() as ExecutionException
        assertSame(cause, ex.cause)
    }

    @Test
    fun registerCallback_isCalledAfterFutureResolution() {
        val future = ResolvableFuture<String>()
        val received = AtomicReference<String?>(null)
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                received.set(response)
            }
            override fun reject(exception: Throwable) {
                fail("unexpected")
            }
        })

        future.resolve("ok")

        assertEquals("ok", received.get())
    }

    @Test
    fun registerCallback_isCalledAfterFutureRejection() {
        val future = ResolvableFuture<String>()
        val cause = IllegalStateException("boom")
        val received = AtomicReference<Throwable?>(null)
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                fail("unexpected resolve $response")
            }
            override fun reject(exception: Throwable) {
                received.set(exception)
            }
        })

        future.reject(cause)

        val ex = received.get() as ExecutionException
        assertSame(cause, ex.cause)
    }

    @Test
    fun multipleCallbacks_areAllInvokedOnResolution() {
        val future = ResolvableFuture<String>()
        val a = AtomicReference<String?>(null)
        val b = AtomicReference<String?>(null)
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                a.set(response)
            }
            override fun reject(exception: Throwable) = fail("unexpected")
        })
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) {
                b.set(response)
            }
            override fun reject(exception: Throwable) = fail("unexpected")
        })

        future.resolve("ok")

        assertEquals("ok", a.get())
        assertEquals("ok", b.get())
    }

    @Test
    fun multipleCallbacks_areAllInvokedOnRejection() {
        val future = ResolvableFuture<String>()
        val a = AtomicReference<Throwable?>(null)
        val b = AtomicReference<Throwable?>(null)
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) = fail("unexpected resolve $response")
            override fun reject(exception: Throwable) {
                a.set(exception)
            }
        })
        future.registerCallback(object : Callback<String> {
            override fun resolve(response: String) = fail("unexpected resolve $response")
            override fun reject(exception: Throwable) {
                b.set(exception)
            }
        })

        future.reject(IllegalStateException("boom"))

        assertNotNull(a.get())
        assertNotNull(b.get())
    }

    @Test
    fun await_returnsResolvedValue() = runBlocking {
        val future = ResolvableFuture<String>()
        future.resolve("ok")

        assertEquals("ok", future.await())
    }

    @Test
    fun awaitOrNull_returnsResolvedValue() = runBlocking {
        val future = ResolvableFuture<String>()
        future.resolve("ok")

        assertEquals("ok", future.awaitOrNull(1, TimeUnit.MILLISECONDS))
    }
}
