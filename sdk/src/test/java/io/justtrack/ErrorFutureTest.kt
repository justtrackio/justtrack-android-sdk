package io.justtrack

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ErrorFutureTest {

    @Test
    fun cancel_alwaysReturnsFalse() {
        val future = ErrorFuture<String>(IllegalStateException("boom"))

        assertFalse(future.cancel(false))
        assertFalse(future.cancel(true))
    }

    @Test
    fun isCancelled_returnsFalse() {
        val future = ErrorFuture<String>(IllegalStateException("boom"))

        assertFalse(future.isCancelled)
    }

    @Test
    fun isDone_returnsTrue() {
        val future = ErrorFuture<String>(IllegalStateException("boom"))

        assertTrue(future.isDone)
    }

    @Test
    fun get_throwsExecutionExceptionWrappingCause() {
        val cause = IllegalStateException("boom")
        val future = ErrorFuture<String>(cause)

        val thrown = assertThrows(ExecutionException::class.java) { future.get() }
        assertSame(cause, thrown.cause)
    }

    @Test
    fun getWithTimeout_throwsSameExecutionExceptionAsGet() {
        val cause = IllegalStateException("boom")
        val future = ErrorFuture<String>(cause)

        val first = assertThrows(ExecutionException::class.java) { future.get() }
        val second = assertThrows(ExecutionException::class.java) { future.get(1, TimeUnit.SECONDS) }

        // Implementation reuses the same wrapping ExecutionException instance.
        assertSame(first, second)
        assertSame(cause, second.cause)
    }

    @Test
    fun await_throwsExecutionExceptionWrappingCause() {
        val cause = RuntimeException("network down")
        val future = ErrorFuture<String>(cause)

        val thrown = assertThrows(ExecutionException::class.java) {
            runBlocking { future.await() }
        }
        assertSame(cause, thrown.cause)
    }

    @Test
    fun awaitOrNull_throwsExecutionExceptionInsteadOfReturningNull() {
        val cause = RuntimeException("nope")
        val future = ErrorFuture<String>(cause)

        val thrown = assertThrows(ExecutionException::class.java) {
            runBlocking { future.awaitOrNull(10, TimeUnit.MILLISECONDS) }
        }
        assertSame(cause, thrown.cause)
    }

    @Test
    fun registerCallback_invokesRejectWithWrappedException() {
        val cause = IllegalArgumentException("bad arg")
        val future = ErrorFuture<String>(cause)
        val rejected = AtomicReference<Throwable?>()
        val resolvedRef = AtomicReference<String?>()

        future.registerCallback(object : Callback<String> {
            override fun resolve(value: String) {
                resolvedRef.set(value)
            }

            override fun reject(exception: Throwable) {
                rejected.set(exception)
            }
        })

        assertNull(resolvedRef.get())
        val captured = rejected.get()
        assertTrue(captured is ExecutionException)
        assertSame(cause, (captured as ExecutionException).cause)
    }

    @Test
    fun registerCallback_passesSameExceptionInstanceAsGet() {
        val cause = RuntimeException("err")
        val future = ErrorFuture<String>(cause)
        val rejected = AtomicReference<Throwable?>()

        future.registerCallback(object : Callback<String> {
            override fun resolve(value: String) = Unit
            override fun reject(exception: Throwable) {
                rejected.set(exception)
            }
        })

        val fromGet = assertThrows(ExecutionException::class.java) { future.get() }
        assertSame(fromGet, rejected.get())
    }

    @Test
    fun get_doesNotDoubleWrapWhenCauseIsAlreadyExecutionException() {
        val inner = RuntimeException("inner")
        val outer = ExecutionException(inner)
        val future = ErrorFuture<String>(outer)

        val thrown = assertThrows(ExecutionException::class.java) { future.get() }
        // ErrorFuture always wraps with a new ExecutionException; cause should be the passed-in throwable.
        assertSame(outer, thrown.cause)
        // sanity: cause-chain still reachable
        assertEquals(inner, thrown.cause!!.cause)
    }
}
