package io.justtrack.crashes

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class CrashHandlerTest {

    private lateinit var crashReporter: CrashReporter
    private lateinit var logger: Logger
    private var originalHandler: UncaughtExceptionHandler? = null
    private val mockDefaultHandler: UncaughtExceptionHandler = mock()
    private val nativeLoader: CrashReportNativeLoader = mock()

    @Before
    fun setUp() {
        crashReporter = mock()
        logger = mock()
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(mockDefaultHandler)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    private fun createHandler(isTracking: Boolean = true) = CrashHandler(
        crashReporter,
        logger,
        AtomicBoolean(isTracking),
        nativeLoader,
    )

    @Test
    fun `uncaughtExceptionHandler captures exception when tracking is enabled`() {
        val handler = createHandler(isTracking = true)
        val exception = RuntimeException("crash")

        handler.getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), exception)

        verify(crashReporter).captureException(exception)
        verify(mockDefaultHandler).uncaughtException(Thread.currentThread(), exception)
    }

    @Test
    fun `uncaughtExceptionHandler does not capture when tracking is disabled`() {
        val handler = createHandler(isTracking = false)
        val exception = RuntimeException("crash")

        handler.getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), exception)

        verify(crashReporter, never()).captureException(any())
        verify(mockDefaultHandler).uncaughtException(Thread.currentThread(), exception)
    }

    @Test
    fun `uncaughtExceptionHandler delegates to defaultHandler`() {
        val handler = createHandler()
        val exception = RuntimeException("crash")

        handler.getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), exception)

        verify(mockDefaultHandler).uncaughtException(Thread.currentThread(), exception)
    }

    @Test
    fun `getUncaughtExceptionHandler returns sdkUncaughtExceptionHandler`() {
        val handler = createHandler()
        assertSame(handler.getUncaughtExceptionHandler(), handler.getUncaughtExceptionHandler())
    }

    @Test
    fun `capturingException reports internal crash to crashReporter`() {
        val handler = createHandler()
        val throwable = RuntimeException("internal")
        // Stack trace contains io.justtrack -> internal crash
        throwable.stackTrace = arrayOf(
            StackTraceElement("io.justtrack.SomeClass", "method", "SomeClass.kt", 10),
        )

        handler.capturingException(throwable)

        verify(crashReporter).captureException(throwable)
        verify(logger, never()).info(any(), any<LoggerFields>())
    }

    @Test
    fun `capturingException drops external crash and logs info`() {
        val handler = createHandler()
        val throwable = RuntimeException("external")
        throwable.stackTrace = arrayOf(
            StackTraceElement("com.external.Lib", "method", "Lib.java", 1),
        )

        handler.capturingException(throwable)

        verify(crashReporter, never()).captureException(any())
        verify(logger).info(eq("Application crash dropped"), any<LoggerFields>())
    }

    // --- defaultHandler null path ---
}
