package io.justtrack.crashes

import java.io.Closeable

internal interface CrashReporter : Closeable {
    fun captureException(throwable: Throwable)
}

internal fun interface CrashReportingFactory {
    fun create(): CrashReporter
}
