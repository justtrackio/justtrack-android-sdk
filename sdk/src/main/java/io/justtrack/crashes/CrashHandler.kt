package io.justtrack.crashes

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles uncaught exceptions by optionally reporting them and then delegating to the original
 * default uncaught exception handler.
 *
 * @property crashReporter The component responsible for reporting uncaught exceptions to a backend etc.
 * @property isTracking A thread-safe flag indicating whether SDK tracking is enabled.
 */
internal class CrashHandler internal constructor(
    private val crashReporter: CrashReporter,
    private val logger: Logger,
    private val isTracking: AtomicBoolean,
    crashReportNativeLoader: CrashReportNativeLoader,
) {

    /**
     * The original uncaught exception handler set by the system or application before installing this one.
     * It will be called after this handler processes the exception.
     */
    private val defaultHandler: UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    /**
     * The SDK's custom uncaught exception handler. It captures exceptions when tracking is enabled
     * reports them to our crash reporting systems and delegates them to the original handler afterward.
     */
    private val sdkUncaughtExceptionHandler =
        UncaughtExceptionHandler { thread, exception ->
            if (isTracking.get()) {
                crashReporter.captureException(exception)
            }

            defaultHandler?.uncaughtException(thread, exception)
        }

    init {
        crashReportNativeLoader.load()
    }

    /**
     * Installs the SDK's custom uncaught exception handler as the default for all threads.
     */
    @JvmName("installUncaughtExceptionHandler")
    internal fun installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(sdkUncaughtExceptionHandler)
    }

    /**
     * Returns the internal uncaught exception handler used by the SDK.
     */
    @JvmName("getUncaughtExceptionHandler")
    internal fun getUncaughtExceptionHandler(): UncaughtExceptionHandler {
        return sdkUncaughtExceptionHandler
    }

    @JvmName("capturingException")
    internal fun capturingException(throwable: Throwable): Void? {
        if (!InternalCrashChecker.isInternalCrash(throwable.stackTraceToString())) {
            val fields = LoggerFieldsBuilder()
            fields.with("stack_trace", throwable.stackTraceToString())
            logger.info("Application crash dropped", fields)

            return null
        }

        crashReporter.captureException(throwable)
        return null
    }
}
