package io.justtrack

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TransformingFutureTest {

    @Test
    fun cancel_delegatesToWrappedFuture() {
        val wrapped = CancellableFuture<String>(cancelResult = true)
        val future = TransformingFuture(wrapped) { it.length }

        assertTrue(future.cancel(true))
        assertTrue(wrapped.cancelCalled)
        assertEquals(true, wrapped.mayInterruptIfRunning)
    }

    @Test
    fun isCancelled_delegatesToWrappedFuture() {
        val wrapped = CancellableFuture<String>(cancelledFlag = true)
        val future = TransformingFuture(wrapped) { it.length }

        assertTrue(future.isCancelled)
    }

    @Test
    fun isDone_delegatesToWrappedFuture() {
        val wrapped = CancellableFuture<String>(doneFlag = true)
        val future = TransformingFuture(wrapped) { it.length }

        assertTrue(future.isDone)
    }

    @Test
    fun get_appliesTransformer() {
        val future = TransformingFuture(ValueFuture("hello")) { it.length }

        assertEquals(5, future.get())
    }

    @Test
    fun getWithTimeout_appliesTransformer() {
        val future = TransformingFuture(ValueFuture("hello!")) { it.length }

        assertEquals(6, future.get(1, TimeUnit.SECONDS))
    }

    @Test
    fun await_appliesTransformer() = runBlocking {
        val future = TransformingFuture(ValueFuture("hi")) { it.length }

        assertEquals(2, future.await())
    }

    @Test
    fun awaitOrNull_appliesTransformerWhenWrappedReturnsValue() = runBlocking {
        val future = TransformingFuture(ValueFuture("hey")) { it.length }

        assertEquals(3, future.awaitOrNull(1, TimeUnit.SECONDS))
    }

    @Test
    fun awaitOrNull_returnsNullWhenWrappedReturnsNull() = runBlocking {
        val wrapped = ConstantNullFuture<String>()
        val future = TransformingFuture<String, Int>(wrapped) { it.length }

        assertNull(future.awaitOrNull(1, TimeUnit.SECONDS))
    }

    @Test
    fun registerCallback_resolvesWithTransformedValue() {
        val future = TransformingFuture(ValueFuture("abcd")) { it.length }
        val received = AtomicReference<Int?>(null)

        future.registerCallback(object : Callback<Int> {
            override fun resolve(response: Int) {
                received.set(response)
            }

            override fun reject(exception: Throwable) {
                throw AssertionError("unexpected reject", exception)
            }
        })

        assertEquals(4, received.get())
    }

    @Test
    fun registerCallback_propagatesRejectionWithoutInvokingTransformer() {
        val rejection = IllegalStateException("boom")
        val wrapped = RejectingFuture<String>(rejection)
        var transformerInvocations = 0
        val future = TransformingFuture<String, Int>(wrapped) {
            transformerInvocations += 1
            it.length
        }
        val received = AtomicReference<Throwable?>(null)

        future.registerCallback(object : Callback<Int> {
            override fun resolve(response: Int) {
                throw AssertionError("unexpected resolve: $response")
            }

            override fun reject(exception: Throwable) {
                received.set(exception)
            }
        })

        assertSame(rejection, received.get())
        assertEquals(0, transformerInvocations)
    }

    // --- helpers ---

    private class CancellableFuture<V>(
        var cancelResult: Boolean = false,
        var cancelledFlag: Boolean = false,
        var doneFlag: Boolean = false,
    ) : AsyncFuture<V> {
        var cancelCalled = false
        var mayInterruptIfRunning = false

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelCalled = true
            this.mayInterruptIfRunning = mayInterruptIfRunning
            return cancelResult
        }

        override fun isCancelled(): Boolean = cancelledFlag
        override fun isDone(): Boolean = doneFlag
        override fun get(): V = throw UnsupportedOperationException()
        override fun get(timeout: Long, unit: TimeUnit): V = throw UnsupportedOperationException()
        override suspend fun await(): V = throw UnsupportedOperationException()
        override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): V? = null
        override fun registerCallback(callback: Callback<V>) = Unit
    }

    private class ConstantNullFuture<V> : AsyncFuture<V> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): V = throw UnsupportedOperationException()
        override fun get(timeout: Long, unit: TimeUnit): V = throw UnsupportedOperationException()
        override suspend fun await(): V = throw UnsupportedOperationException()
        override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): V? = null
        override fun registerCallback(callback: Callback<V>) = Unit
    }

    private class RejectingFuture<V>(private val exception: Throwable) : AsyncFuture<V> {
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = false
        override fun isDone(): Boolean = true
        override fun get(): V = throw ExecutionException(exception)
        override fun get(timeout: Long, unit: TimeUnit): V = throw ExecutionException(exception)
        override suspend fun await(): V = throw exception
        override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): V? = null
        override fun registerCallback(callback: Callback<V>) {
            callback.reject(exception)
        }
    }
}
