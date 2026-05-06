package io.justtrack.crashes

internal object InternalCrashChecker {
    internal fun isInternalCrash(stacktrace: String?): Boolean {
        if (stacktrace.isNullOrEmpty()) {
            // If there is multiple external crash without stacktrace then this can be remove later.
            return true
        }

        // Obfuscation of class name still preserve the package name.
        // io_justtrack is for native crash
        return stacktrace.contains("io.justtrack") || stacktrace.contains("io_justtrack")
    }
}
