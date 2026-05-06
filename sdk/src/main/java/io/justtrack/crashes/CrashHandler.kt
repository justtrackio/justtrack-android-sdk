package io.justtrack.crashes

import android.os.Looper
import io.justtrack.JtCrashReporter.Companion.ANR_TIMEOUT
import io.justtrack.JtCrashReporter.Companion.STACKTRACE_FILE_PREFIX
import io.justtrack.exceptions.ANRException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    packageName: String,
    private val crashReporter: CrashReporter,
    private val logger: Logger,
    private val isTracking: AtomicBoolean,
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

    private var isAppResponding = AtomicBoolean(true)
    private var anrCurrentJob: Job? = null
    private var anrCheckJob: Job? = null

    init {
        System.loadLibrary("lib-crash-report")
        registerListener(packageName, STACKTRACE_FILE_PREFIX)
    }

    /**
     * Installs the SDK's custom uncaught exception handler as the default for all threads.
     */
    fun installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(sdkUncaughtExceptionHandler)
    }

    @JvmName("onResume")
    internal fun onResume() {
        startANRDetection()
    }

    @JvmName("onPause")
    internal fun onPause() {
        stopANRDetection()
    }

    /**
     * Returns the internal uncaught exception handler used by the SDK.
     */
    @JvmName("getUncaughtExceptionHandler")
    internal fun getUncaughtExceptionHandler(): UncaughtExceptionHandler {
        return sdkUncaughtExceptionHandler
    }

    private fun startANRDetector() {
        cancelJob(anrCurrentJob)

        anrCurrentJob = CoroutineScope(Dispatchers.IO).launch {
            do {
                cancelJob(anrCheckJob)
                anrCheckJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(ANR_TIMEOUT)
                    isAppResponding.set(true)
                }

                delay(ANR_TIMEOUT + ANR_TIMEOUT)
            } while (isAppResponding.getAndSet(false))

            onANRDetectCallback()

            // Check the Main thread's responsiveness
            waitMainThread()

            // Restart the ANR detector loop
            startANRDetector()
        }
    }

    private suspend fun waitMainThread() {
        val isMainThreadCompleted = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            isMainThreadCompleted.complete(true)
        }
        isMainThreadCompleted.await()
    }

    private fun startANRDetection() {
        startANRDetector()
    }

    private fun stopANRDetection() {
        cancelJob(anrCurrentJob)
        cancelJob(anrCheckJob)
    }

    private fun cancelJob(job: Job?) {
        job?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
    }

    private fun onANRDetectCallback() {
        val stackTrace = Looper.getMainLooper().thread.stackTrace
        val message = StringBuilder("Detected ANR:\n\n")

        for ((thread, stacktrace) in Thread.getAllStackTraces()) {
            val id = thread.id
            val name = thread.name
            val state = thread.state
            val daemon = thread.isDaemon
            val priority = thread.priority

            val reportThread =
                when (state) {
                    Thread.State.BLOCKED -> true
                    Thread.State.NEW -> false
                    Thread.State.RUNNABLE -> false
                    Thread.State.TERMINATED -> false
                    Thread.State.TIMED_WAITING -> false
                    Thread.State.WAITING -> !daemon
                    null -> false
                }

            if (!reportThread) {
                continue
            }

            message.append("Thread $id (priority = $priority, name = $name${if (daemon) ", daemon" else ""}) in state $state")
            for (elem in stacktrace) {
                message.append("  at ").append(elem.toString()).append('\n')
            }
            message.append('\n')
        }

        message.append("=== Finished thread dump ===")

        capturingException(ANRException(message.toString(), stackTrace))
    }

    private fun capturingException(throwable: Throwable) {
        if (!InternalCrashChecker.isInternalCrash(throwable.stackTraceToString())) {
            val fields = LoggerFieldsBuilder()
            fields.with("stack_trace", throwable.stackTraceToString())
            logger.info("Application crash dropped", fields)

            return
        }

        crashReporter.captureException(throwable)
    }

    external fun registerListener(applicationPackageName: String, stacktraceFilePrefix: String)
}
