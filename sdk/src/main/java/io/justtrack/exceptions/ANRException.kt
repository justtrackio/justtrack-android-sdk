package io.justtrack.exceptions

internal class ANRException internal constructor(message: String, stacktrace: Array<StackTraceElement>) : Exception(message) {
    init {
        setStackTrace(stacktrace)
    }
}
