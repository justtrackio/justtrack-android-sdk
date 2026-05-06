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

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler(mockDefaultHandler)

        crashHandler = CrashHandler("io.justtrack.test", crashReporter, TestLoggerImpl(), isTracking)
    }

    @After
    fun tearDown() {
        // Restore the original handler to avoid side effects
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

        // Our crash reporting doesn't get exceptions
        verify(crashReporter, never()).captureException(any())
        // Default exception handler still captures exception so that apps can handle them
        verify(mockDefaultHandler).uncaughtException(Thread.currentThread(), testException)
    }

    // installs
    private fun installUncaughtExceptionHandler(): UncaughtExceptionHandler {
        crashHandler.installUncaughtExceptionHandler()

        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertSame(handler, crashHandler.getUncaughtExceptionHandler())

        return handler!!
    }
}
