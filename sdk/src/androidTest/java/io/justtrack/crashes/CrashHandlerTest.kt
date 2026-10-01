package io.justtrack.crashes

import io.justtrack.TestLoggerImpl
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean

class CrashHandlerTest {
    private val crashReporter = mock<CrashReporter>()
    private val isTracking = AtomicBoolean(true)

    private var originalHandler: UncaughtExceptionHandler? = null
    private val mockDefaultHandler: UncaughtExceptionHandler = mock()
    private lateinit var crashHandler: CrashHandler
    private lateinit var logger: TestLoggerImpl
    private val nativeLoader = CrashReportNativeLoaderImpl("io.justtrack.test")

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler(mockDefaultHandler)

        logger = TestLoggerImpl()
        crashHandler = CrashHandler(
            crashReporter,
            logger,
            isTracking,
            nativeLoader,
        )
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun uncaughtException_should_report_when_tracking_is_enabled() {
        val testException = RuntimeException("Test exception")

        val handler = installUncaughtExceptionHandler()

        handler.uncaughtException(Thread.currentThread(), testException)

        verify(crashReporter).captureException(testException)
        verify(mockDefaultHandler).uncaughtException(Thread.currentThread(), testException)
    }

    @Test
    fun uncaughtException_should_not_report_when_tracking_is_disabled() {
        isTracking.set(false)
        val testException = RuntimeException("Test exception")

        val handler = installUncaughtExceptionHandler()

        handler.uncaughtException(Thread.currentThread(), testException)

        verify(crashReporter, never()).captureException(any())
        verify(mockDefaultHandler).uncaughtException(Thread.currentThread(), testException)
    }

    @Test
    fun capturingException_reports_internal_crash() {
        // Exception with io.justtrack in stack trace -> internal crash -> should report
        val exception = RuntimeException("internal crash")
        // The stack trace will contain io.justtrack since we're in this package
        crashHandler.capturingException(exception)

        verify(crashReporter).captureException(exception)
    }

    @Test
    fun capturingException_drops_external_crash_and_logs() {
        // Create an exception whose stack trace does NOT contain io.justtrack
        val exception = RuntimeException("external crash")
        // Override the stack trace to not contain io.justtrack
        exception.stackTrace = arrayOf(
            StackTraceElement("com.external.SomeClass", "someMethod", "SomeClass.java", 42),
        )

        crashHandler.capturingException(exception)

        verify(crashReporter, never()).captureException(any())
    }

    private fun installUncaughtExceptionHandler(): UncaughtExceptionHandler {
        crashHandler.installUncaughtExceptionHandler()

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertSame(handler, crashHandler.getUncaughtExceptionHandler())

        return handler!!
    }
}
