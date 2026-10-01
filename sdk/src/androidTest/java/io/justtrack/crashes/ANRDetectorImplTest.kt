package io.justtrack.crashes

import io.justtrack.exceptions.ANRException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class ANRDetectorImplTest {

    private val reportedThrowable = AtomicReference<Throwable?>(null)
    private val fakeMainStackTrace = arrayOf(
        StackTraceElement("io.justtrack.SomeClass", "someMethod", "SomeClass.kt", 10),
    )

    private val fakeAllThreads = listOf(
        ThreadStacktrace(
            id = 1L,
            name = "BlockedThread",
            daemon = false,
            priority = 5,
            threadState = Thread.State.BLOCKED,
            stacktrace = arrayOf(
                StackTraceElement("com.example.Foo", "bar", "Foo.kt", 20),
            ),
        ),
        ThreadStacktrace(
            id = 2L,
            name = "WaitingNonDaemon",
            daemon = false,
            priority = 5,
            threadState = Thread.State.WAITING,
            stacktrace = arrayOf(
                StackTraceElement("com.example.Baz", "qux", "Baz.kt", 30),
            ),
        ),
        ThreadStacktrace(
            id = 3L,
            name = "WaitingDaemon",
            daemon = true,
            priority = 5,
            threadState = Thread.State.WAITING,
            stacktrace = arrayOf(
                StackTraceElement("com.example.Daemon", "run", "Daemon.kt", 40),
            ),
        ),
        ThreadStacktrace(
            id = 4L,
            name = "RunnableThread",
            daemon = false,
            priority = 5,
            threadState = Thread.State.RUNNABLE,
            stacktrace = arrayOf(
                StackTraceElement("com.example.Run", "exec", "Run.kt", 50),
            ),
        ),
        ThreadStacktrace(
            id = 5L,
            name = "NewThread",
            daemon = false,
            priority = 5,
            threadState = Thread.State.NEW,
            stacktrace = emptyArray(),
        ),
        ThreadStacktrace(
            id = 6L,
            name = "TerminatedThread",
            daemon = false,
            priority = 5,
            threadState = Thread.State.TERMINATED,
            stacktrace = emptyArray(),
        ),
        ThreadStacktrace(
            id = 7L,
            name = "TimedWaitingThread",
            daemon = false,
            priority = 5,
            threadState = Thread.State.TIMED_WAITING,
            stacktrace = arrayOf(
                StackTraceElement("com.example.Timed", "wait", "Timed.kt", 60),
            ),
        ),
    )

    private val fakeStackTraceProvider = object : StacktraceProvider {
        override fun provideMainStacktrace(): Array<StackTraceElement> = fakeMainStackTrace
        override fun provideAllStacktrace(): List<ThreadStacktrace> = fakeAllThreads
    }

    private lateinit var detector: ANRDetectorImpl

    @Before
    fun setUp() {
        reportedThrowable.set(null)
        detector = ANRDetectorImpl(
            onReportAnr = {
                reportedThrowable.set(it)
                null
            },
            stacktraceProvider = fakeStackTraceProvider,
        )
    }

    @After
    fun tearDown() {
        detector.stop()
    }

    @Test
    fun onANRDetectCallback_reports_anr_exception() {
        detector.onANRDetectCallback()

        val throwable = reportedThrowable.get()
        assertNotNull(throwable)
        assertTrue(throwable is ANRException)
    }

    @Test
    fun onANRDetectCallback_message_contains_anr_header() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertTrue(message.contains("Detected ANR:"))
    }

    @Test
    fun onANRDetectCallback_message_contains_finished_marker() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertTrue(message.contains("=== Finished thread dump ==="))
    }

    @Test
    fun onANRDetectCallback_uses_stacktrace_provider_for_exception_trace() {
        detector.onANRDetectCallback()

        val anr = reportedThrowable.get() as ANRException
        assertEquals(fakeMainStackTrace.size, anr.stackTrace.size)
        assertEquals(fakeMainStackTrace[0], anr.stackTrace[0])
    }

    @Test
    fun onANRDetectCallback_includes_blocked_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertTrue("Blocked thread should appear in dump", message.contains("BlockedThread"))
        assertTrue("Should show BLOCKED state", message.contains("BLOCKED"))
    }

    @Test
    fun onANRDetectCallback_includes_waiting_non_daemon_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertTrue("Waiting non-daemon should appear", message.contains("WaitingNonDaemon"))
    }

    @Test
    fun onANRDetectCallback_excludes_daemon_waiting_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertFalse("Daemon waiting thread should NOT appear", message.contains("WaitingDaemon"))
    }

    @Test
    fun onANRDetectCallback_excludes_runnable_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertFalse("Runnable thread should NOT appear", message.contains("RunnableThread"))
    }

    @Test
    fun onANRDetectCallback_excludes_new_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertFalse("New thread should NOT appear", message.contains("NewThread"))
    }

    @Test
    fun onANRDetectCallback_excludes_terminated_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertFalse("Terminated thread should NOT appear", message.contains("TerminatedThread"))
    }

    @Test
    fun onANRDetectCallback_excludes_timed_waiting_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertFalse("Timed waiting thread should NOT appear", message.contains("TimedWaitingThread"))
    }

    @Test
    fun onANRDetectCallback_includes_stack_trace_elements_for_reported_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertTrue("Should contain blocked thread's stack element", message.contains("com.example.Foo.bar"))
        assertTrue("Should contain waiting non-daemon's stack element", message.contains("com.example.Baz.qux"))
    }

    @Test
    fun onANRDetectCallback_shows_daemon_label_for_daemon_threads_when_reported() {
        // Use a provider where a BLOCKED daemon thread would be reported
        val daemonBlockedProvider = object : StacktraceProvider {
            override fun provideMainStacktrace(): Array<StackTraceElement> = fakeMainStackTrace
            override fun provideAllStacktrace(): List<ThreadStacktrace> = listOf(
                ThreadStacktrace(
                    id = 10L,
                    name = "DaemonBlocked",
                    daemon = true,
                    priority = 3,
                    threadState = Thread.State.BLOCKED,
                    stacktrace = emptyArray(),
                ),
            )
        }
        val testDetector = ANRDetectorImpl(
            onReportAnr = {
                reportedThrowable.set(it)
                null
            },
            stacktraceProvider = daemonBlockedProvider,
        )

        testDetector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        assertTrue("Should contain daemon label", message.contains(", daemon"))
    }

    @Test
    fun onANRDetectCallback_omits_daemon_label_for_non_daemon_threads() {
        detector.onANRDetectCallback()

        val message = reportedThrowable.get()!!.message!!
        // BlockedThread is non-daemon, its entry should NOT have ", daemon"
        val blockedEntry = message.lines().find { it.contains("BlockedThread") }
        assertNotNull(blockedEntry)
        assertFalse("Non-daemon should not have daemon label", blockedEntry!!.contains(", daemon"))
    }

    @Test
    fun stop_cancels_detection() {
        detector.start()
        detector.stop()

        Thread.sleep(200)
        assertNull("No ANR should be reported after stop", reportedThrowable.get())
    }

    @Test
    fun stop_can_be_called_multiple_times_safely() {
        detector.start()
        detector.stop()
        detector.stop()
    }

    @Test
    fun start_can_be_called_multiple_times() {
        detector.start()
        detector.start()
        detector.stop()
    }

    @Test
    fun cancelJob_with_null_does_not_throw() {
        detector.cancelJob(null)
    }

    @Test
    fun cancelJob_with_inactive_job_does_not_throw() = runBlocking {
        val job = launch { }
        job.join()
        assertFalse(job.isActive)
        detector.cancelJob(job)
    }

    @Test
    fun cancelJob_cancels_active_job() = runBlocking {
        val job = launch { kotlinx.coroutines.delay(10000) }
        assertTrue(job.isActive)
        detector.cancelJob(job)
        assertFalse(job.isActive)
    }
}
