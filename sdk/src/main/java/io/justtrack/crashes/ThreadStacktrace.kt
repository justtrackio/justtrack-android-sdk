package io.justtrack.crashes

internal data class ThreadStacktrace(
    val id: Long,
    val name: String,
    val daemon: Boolean,
    val priority: Int,
    val threadState: Thread.State,
    val stacktrace: Array<StackTraceElement>,
)
