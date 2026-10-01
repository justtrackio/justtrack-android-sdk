package io.justtrack.crashes

import io.justtrack.JtCrashReporter.Companion.STACKTRACE_FILE_PREFIX
import io.justtrack.util.ExcludeFromJacocoGeneratedReport

@ExcludeFromJacocoGeneratedReport
internal class CrashReportNativeLoaderImpl(private val packageName: String) : CrashReportNativeLoader {
    override fun load() {
        System.loadLibrary("lib-crash-report")
        registerListener(packageName, STACKTRACE_FILE_PREFIX)
    }

    external fun registerListener(applicationPackageName: String, stacktraceFilePrefix: String)
}
