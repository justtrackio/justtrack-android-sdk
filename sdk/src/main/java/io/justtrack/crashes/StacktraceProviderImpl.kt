package io.justtrack.crashes

import android.os.Looper
import io.justtrack.util.ExcludeFromJacocoGeneratedReport

@ExcludeFromJacocoGeneratedReport
internal class StacktraceProviderImpl : StacktraceProvider {
    override fun provideMainStacktrace(): Array<StackTraceElement> {
        return Looper.getMainLooper().thread.stackTrace
    }

    override fun provideAllStacktrace(): List<ThreadStacktrace> {
        val allThreadStacktrace = ArrayList<ThreadStacktrace>()
        for ((thread, stacktrace) in Thread.getAllStackTraces()) {
            allThreadStacktrace.add(
                ThreadStacktrace(
                    id = thread.id,
                    name = thread.name,
                    daemon = thread.isDaemon,
                    priority = thread.priority,
                    threadState = thread.state,
                    stacktrace,
                ),
            )
        }
        return allThreadStacktrace
    }
}
