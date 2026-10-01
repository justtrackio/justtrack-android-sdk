package io.justtrack.executor

import android.os.HandlerThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SerializeHandlerThreadImplTest {

    @Test
    fun constructor_startsHandlerThreadWithExpectedName() {
        SerializeHandlerThreadImpl()
        val running = Thread.getAllStackTraces().keys.map { it.name }
        assertTrue(
            "Expected a thread named 'SerializedCallbackInvokerThread', running=$running",
            running.contains("SerializedCallbackInvokerThread"),
        )
    }

    @Test
    fun run_postsRunnableToTheBackingHandler() {
        val subject: SerializeHandlerThread = SerializeHandlerThreadImpl()
        val ran = AtomicReference<Boolean>(false)
        val latch = CountDownLatch(1)
        val runnable = Runnable {
            ran.set(true)
            latch.countDown()
        }

        subject.run(runnable)

        // Drain the handler thread's looper (Robolectric PAUSED mode otherwise queues forever).
        idleAllHandlerThreads()

        assertTrue("Runnable did not execute", latch.await(5, TimeUnit.SECONDS))
        assertEquals(true, ran.get())
    }

    @Test
    fun run_executesMultipleRunnablesInPostedOrder() {
        val subject = SerializeHandlerThreadImpl()
        val order = mutableListOf<Int>()
        val latch = CountDownLatch(3)

        subject.run {
            synchronized(order) { order.add(1) }
            latch.countDown()
        }
        subject.run {
            synchronized(order) { order.add(2) }
            latch.countDown()
        }
        subject.run {
            synchronized(order) { order.add(3) }
            latch.countDown()
        }

        idleAllHandlerThreads()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2, 3), order)
    }

    /**
     * Robolectric defaults to PAUSED LooperMode; any HandlerThread the SUT creates needs its
     * looper drained explicitly for posted runnables to execute.
     */
    private fun idleAllHandlerThreads() {
        Thread.getAllStackTraces().keys
            .filterIsInstance<HandlerThread>()
            .filter { it.name == "SerializedCallbackInvokerThread" }
            .forEach { shadowOf(it.looper).idle() }
    }
}
