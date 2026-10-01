package io.justtrack.crashes

import io.justtrack.JtCrashReporter.Companion.ANR_TIMEOUT
import io.justtrack.exceptions.ANRException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

internal class ANRDetectorImpl(
    @VisibleForTesting val onReportAnr: (Throwable) -> Void?,
    private val stacktraceProvider: StacktraceProvider,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val mainContext: CoroutineContext = Dispatchers.Main,
) : ANRDetector {

    constructor(
        onReportAnr: (Throwable) -> Void?,
        stacktraceProvider: StacktraceProvider,
    ) : this(
        onReportAnr,
        stacktraceProvider,
        Dispatchers.IO,
        Dispatchers.Main,
    )

    private var isAppResponding = AtomicBoolean(true)

    @VisibleForTesting var anrCurrentJob: Job? = null
    private var anrCheckJob: Job? = null

    override fun start() {
        cancelJob(anrCurrentJob)

        anrCurrentJob = CoroutineScope(ioContext).launch {
            while (true) {
                do {
                    cancelJob(anrCheckJob)
                    anrCheckJob = CoroutineScope(mainContext).launch {
                        delay(ANR_TIMEOUT)
                        isAppResponding.set(true)
                    }

                    delay(ANR_TIMEOUT + ANR_TIMEOUT)
                } while (isAppResponding.getAndSet(false))

                onANRDetectCallback()

                // Check the Main thread's responsiveness
                waitMainThread()
            }
        }
    }

    override fun stop() {
        cancelJob(anrCurrentJob)
        cancelJob(anrCheckJob)
    }

    private suspend fun waitMainThread(scope: CoroutineScope = CoroutineScope(mainContext)) {
        val isMainThreadCompleted = CompletableDeferred<Boolean>()
        scope.launch {
            isMainThreadCompleted.complete(true)
        }
        isMainThreadCompleted.await()
    }

    internal fun onANRDetectCallback() {
        val stackTrace = stacktraceProvider.provideMainStacktrace()
        val message = StringBuilder("Detected ANR:\n\n")

        for (thread in stacktraceProvider.provideAllStacktrace()) {
            val id = thread.id
            val name = thread.name
            val state = thread.threadState
            val daemon = thread.daemon
            val priority = thread.priority

            val reportThread =
                when (state) {
                    Thread.State.BLOCKED -> true
                    Thread.State.NEW -> false
                    Thread.State.RUNNABLE -> false
                    Thread.State.TERMINATED -> false
                    Thread.State.TIMED_WAITING -> false
                    Thread.State.WAITING -> !daemon
                }

            if (!reportThread) {
                continue
            }

            message.append("Thread $id (priority = $priority, name = $name${if (daemon) ", daemon" else ""}) in state $state")
            for (elem in thread.stacktrace) {
                message.append("  at ").append(elem.toString()).append('\n')
            }
            message.append('\n')
        }

        message.append("=== Finished thread dump ===")
        onReportAnr.invoke(ANRException(message.toString(), stackTrace))
    }

    internal fun cancelJob(job: Job?) {
        job?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
    }
}
