package io.justtrack.crashes

import io.justtrack.JtCrashReporter.Companion.ANR_TIMEOUT
import io.justtrack.exceptions.ANRException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class ANRDetectorImplTest {

    private val fakeMainStackTrace = arrayOf(
        StackTraceElement("io.justtrack.Main", "run", "Main.kt", 5),
    )

    private fun createDetectorWithThreads(threads: List<ThreadStacktrace>): Pair<ANRDetectorImpl, () -> Throwable?> {
        var reported: Throwable? = null
        val provider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = fakeMainStackTrace
            override fun provideAllStacktrace(): List<ThreadStacktrace> = threads
        }
        val detector = ANRDetectorImpl(
            onReportAnr = {
                reported = it
                null
            },
            stacktraceProvider = provider,
        )
        return detector to { reported }
    }

    // --- onANRDetectCallback: basic behavior ---

    @Test
    fun `onANRDetectCallback reports ANRException`() {
        val (detector, getReported) = createDetectorWithThreads(emptyList())
        detector.onANRDetectCallback()

        val throwable = getReported()
        assertNotNull(throwable)
        assertTrue(throwable is ANRException)
    }

    @Test
    fun `onANRDetectCallback message contains header and footer`() {
        val (detector, getReported) = createDetectorWithThreads(emptyList())
        detector.onANRDetectCallback()

        val message = getReported()!!.message!!
        assertTrue(message.contains("Detected ANR:"))
        assertTrue(message.contains("=== Finished thread dump ==="))
    }

    @Test
    fun `onANRDetectCallback uses provider main stack trace`() {
        val (detector, getReported) = createDetectorWithThreads(emptyList())
        detector.onANRDetectCallback()

        val anr = getReported() as ANRException
        assertEquals(fakeMainStackTrace.size, anr.stackTrace.size)
        assertEquals(fakeMainStackTrace[0], anr.stackTrace[0])
    }

    // --- onANRDetectCallback: BLOCKED thread (always reported) ---

    @Test
    fun `onANRDetectCallback includes BLOCKED non-daemon thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(1L, "BlockedNonDaemon", false, 5, Thread.State.BLOCKED, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertTrue(getReported()!!.message!!.contains("BlockedNonDaemon"))
    }

    @Test
    fun `onANRDetectCallback includes BLOCKED daemon thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(2L, "BlockedDaemon", true, 5, Thread.State.BLOCKED, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertTrue(getReported()!!.message!!.contains("BlockedDaemon"))
    }

    // --- onANRDetectCallback: WAITING thread (only non-daemon) ---

    @Test
    fun `onANRDetectCallback includes WAITING non-daemon thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(3L, "WaitingNonDaemon", false, 5, Thread.State.WAITING, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertTrue(getReported()!!.message!!.contains("WaitingNonDaemon"))
    }

    @Test
    fun `onANRDetectCallback excludes WAITING daemon thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(4L, "WaitingDaemon", true, 5, Thread.State.WAITING, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertFalse(getReported()!!.message!!.contains("WaitingDaemon"))
    }

    // --- onANRDetectCallback: excluded states ---

    @Test
    fun `onANRDetectCallback excludes NEW thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(5L, "NewThread", false, 5, Thread.State.NEW, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertFalse(getReported()!!.message!!.contains("NewThread"))
    }

    @Test
    fun `onANRDetectCallback excludes RUNNABLE thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(6L, "RunnableThread", false, 5, Thread.State.RUNNABLE, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertFalse(getReported()!!.message!!.contains("RunnableThread"))
    }

    @Test
    fun `onANRDetectCallback excludes TERMINATED thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(7L, "TerminatedThread", false, 5, Thread.State.TERMINATED, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertFalse(getReported()!!.message!!.contains("TerminatedThread"))
    }

    @Test
    fun `onANRDetectCallback excludes TIMED_WAITING thread`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(8L, "TimedWaiting", false, 5, Thread.State.TIMED_WAITING, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        assertFalse(getReported()!!.message!!.contains("TimedWaiting"))
    }

    // --- onANRDetectCallback: message formatting ---

    @Test
    fun `onANRDetectCallback formats thread info with id priority name and state`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(99L, "MyThread", false, 7, Thread.State.BLOCKED, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        val message = getReported()!!.message!!
        assertTrue(message.contains("Thread 99 (priority = 7, name = MyThread) in state BLOCKED"))
    }

    @Test
    fun `onANRDetectCallback includes daemon label for daemon threads`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(10L, "DaemonBlocked", true, 3, Thread.State.BLOCKED, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        val message = getReported()!!.message!!
        assertTrue(message.contains("name = DaemonBlocked, daemon"))
    }

    @Test
    fun `onANRDetectCallback omits daemon label for non-daemon threads`() {
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(11L, "NonDaemon", false, 5, Thread.State.BLOCKED, emptyArray()),
            ),
        )
        detector.onANRDetectCallback()

        val message = getReported()!!.message!!
        assertFalse(message.contains(", daemon"))
    }

    @Test
    fun `onANRDetectCallback includes stack trace elements for reported threads`() {
        val trace = arrayOf(
            StackTraceElement("com.example.Foo", "bar", "Foo.kt", 42),
            StackTraceElement("com.example.Baz", "qux", "Baz.kt", 10),
        )
        val (detector, getReported) = createDetectorWithThreads(
            listOf(
                ThreadStacktrace(12L, "TracedThread", false, 5, Thread.State.BLOCKED, trace),
            ),
        )
        detector.onANRDetectCallback()

        val message = getReported()!!.message!!
        assertTrue(message.contains("at com.example.Foo.bar(Foo.kt:42)"))
        assertTrue(message.contains("at com.example.Baz.qux(Baz.kt:10)"))
    }

    @Test
    fun `onANRDetectCallback with empty thread list produces header and footer only`() {
        val (detector, getReported) = createDetectorWithThreads(emptyList())
        detector.onANRDetectCallback()

        val message = getReported()!!.message!!
        assertTrue(message.startsWith("Detected ANR:"))
        assertTrue(message.endsWith("=== Finished thread dump ==="))
    }

    @Test
    fun `onANRDetectCallback with multiple threads only reports qualifying ones`() {
        val threads = listOf(
            ThreadStacktrace(1L, "Blocked1", false, 5, Thread.State.BLOCKED, emptyArray()),
            ThreadStacktrace(2L, "Runnable1", false, 5, Thread.State.RUNNABLE, emptyArray()),
            ThreadStacktrace(3L, "WaitNonDaemon", false, 5, Thread.State.WAITING, emptyArray()),
            ThreadStacktrace(4L, "WaitDaemon", true, 5, Thread.State.WAITING, emptyArray()),
            ThreadStacktrace(5L, "Blocked2", true, 5, Thread.State.BLOCKED, emptyArray()),
        )
        val (detector, getReported) = createDetectorWithThreads(threads)
        detector.onANRDetectCallback()

        val message = getReported()!!.message!!
        assertTrue(message.contains("Blocked1"))
        assertFalse(message.contains("Runnable1"))
        assertTrue(message.contains("WaitNonDaemon"))
        assertFalse(message.contains("WaitDaemon"))
        assertTrue(message.contains("Blocked2"))
    }

    // --- constructor properties ---

    @Test
    fun `anrReportingFunction is stored correctly`() {
        var called = false
        val func: (Throwable) -> Void? = {
            called = true
            null
        }
        val provider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = emptyArray()
            override fun provideAllStacktrace(): List<ThreadStacktrace> = emptyList()
        }
        val detector = ANRDetectorImpl(
            onReportAnr = func,
            stacktraceProvider = provider,
        )

        detector.onReportAnr(RuntimeException())
        assertTrue(called)
    }

    @Test
    fun `implements ANRDetector interface`() {
        val provider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = emptyArray()
            override fun provideAllStacktrace(): List<ThreadStacktrace> = emptyList()
        }
        val detector = ANRDetectorImpl(
            onReportAnr = { null },
            stacktraceProvider = provider,
        )

        assertTrue(detector is ANRDetector)
    }

    // --- initial state ---

    @Test
    fun `anrCurrentJob initial value is null`() {
        val provider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = emptyArray()
            override fun provideAllStacktrace(): List<ThreadStacktrace> = emptyList()
        }
        val detector = ANRDetectorImpl(
            onReportAnr = { null },
            stacktraceProvider = provider,
        )

        assertNull(detector.anrCurrentJob)
    }

    // --- cancelJob ---

    @Test
    fun `cancelJob with null does not throw`() {
        val (detector, _) = createDetectorWithThreads(emptyList())
        detector.cancelJob(null)
    }

    @Test
    fun `cancelJob cancels active job`() = runBlocking {
        val (detector, _) = createDetectorWithThreads(emptyList())
        val job = launch { delay(10000) }

        assertTrue(job.isActive)
        detector.cancelJob(job)
        assertFalse(job.isActive)
    }

    @Test
    fun `cancelJob with inactive job does not throw`() = runBlocking {
        val (detector, _) = createDetectorWithThreads(emptyList())
        val job = launch { }
        job.join()

        assertFalse(job.isActive)
        detector.cancelJob(job)
        // No exception means the isActive check prevented cancel from being called
    }

    // --- start() loop tests using test dispatchers ---

    private fun createDetectorWithTestDispatchers(testScope: TestScope): Pair<ANRDetectorImpl, () -> Throwable?> {
        var reported: Throwable? = null
        val provider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = fakeMainStackTrace
            override fun provideAllStacktrace(): List<ThreadStacktrace> = emptyList()
        }
        val dispatcher = StandardTestDispatcher(testScope.testScheduler)
        val detector = ANRDetectorImpl(
            onReportAnr = {
                reported = it
                null
            },
            stacktraceProvider = provider,
            ioContext = dispatcher,
            mainContext = dispatcher,
        )
        return detector to { reported }
    }

    @Test
    fun `start detects ANR when main thread is unresponsive`() = runTest {
        var reported: Throwable? = null
        val provider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = fakeMainStackTrace
            override fun provideAllStacktrace(): List<ThreadStacktrace> = emptyList()
        }
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        // Use a separate scheduler for main so anrCheckJob doesn't auto-advance
        val mainScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val detector = ANRDetectorImpl(
            onReportAnr = {
                reported = it
                null
            },
            stacktraceProvider = provider,
            ioContext = ioDispatcher,
            mainContext = mainDispatcher,
        )

        detector.start()

        // First iteration: isAppResponding is true (initial), getAndSet(false) returns true → loop continues
        // Second iteration: anrCheckJob never ran (different scheduler), getAndSet(false) returns false → ANR
        testScheduler.advanceTimeBy(ANR_TIMEOUT * 4 + 1)
        testScheduler.runCurrent()

        // ANR detected because main never responded
        assertNotNull("ANR callback should have been invoked", reported)
        assertTrue(reported is ANRException)

        detector.stop()
    }

    @Test
    fun `start does not trigger ANR when app is responding`() = runTest {
        var reported: Throwable? = null
        val provider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = fakeMainStackTrace
            override fun provideAllStacktrace(): List<ThreadStacktrace> = emptyList()
        }
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        // Share same scheduler so main job completes its delay before IO delay finishes
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val detector = ANRDetectorImpl(
            onReportAnr = {
                reported = it
                null
            },
            stacktraceProvider = provider,
            ioContext = ioDispatcher,
            mainContext = mainDispatcher,
        )

        detector.start()

        // Advance just past one iteration — anrCheckJob delay(ANR_TIMEOUT) fires before
        // the outer delay(ANR_TIMEOUT*2), setting isAppResponding=true
        testScheduler.advanceTimeBy(ANR_TIMEOUT * 2 + 1)
        testScheduler.runCurrent()

        assertNull("ANR should NOT be reported when app is responding", reported)

        detector.stop()
    }

    @Test
    fun `start cancels previous anrCurrentJob on restart`() = runTest {
        val testScope = TestScope(testScheduler)
        val (detector, _) = createDetectorWithTestDispatchers(testScope)

        detector.start()
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        val firstJob = detector.anrCurrentJob
        assertNotNull(firstJob)
        assertTrue(firstJob!!.isActive)

        detector.start()
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        assertFalse("First job should be cancelled", firstJob.isActive)
        assertNotNull(detector.anrCurrentJob)
        assertTrue(detector.anrCurrentJob!!.isActive)

        detector.stop()
    }

    @Test
    fun `start loop detects ANR then restarts and detects again`() = runTest {
        var reportCount = 0
        val provider = Mockito.mock(StacktraceProvider::class.java)
        Mockito.`when`(provider.provideMainStacktrace()).thenReturn(fakeMainStackTrace)
        Mockito.`when`(provider.provideAllStacktrace()).thenReturn(emptyList())

        val ioDispatcher = StandardTestDispatcher(testScheduler)
        // Separate main scheduler — we control when main "responds"
        val mainScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val mainDispatcher = StandardTestDispatcher(mainScheduler)
        val detector = ANRDetectorImpl(
            onReportAnr = {
                reportCount++
                null
            },
            stacktraceProvider = provider,
            ioContext = ioDispatcher,
            mainContext = mainDispatcher,
        )

        detector.start()

        // Two iterations needed: first returns true (initial), second returns false → ANR
        testScheduler.advanceTimeBy(ANR_TIMEOUT * 4 + 1)
        testScheduler.runCurrent()
        assertEquals("First ANR should be detected", 1, reportCount)

        // After ANR, waitMainThread is waiting for main to respond.
        // Advance main scheduler to let waitMainThread complete.
        mainScheduler.advanceUntilIdle()
        testScheduler.runCurrent()

        // Second ANR: again two iterations needed
        testScheduler.advanceTimeBy(ANR_TIMEOUT * 4 + 1)
        testScheduler.runCurrent()
        assertEquals("Second ANR should be detected after restart", 2, reportCount)

        detector.stop()
    }
}
