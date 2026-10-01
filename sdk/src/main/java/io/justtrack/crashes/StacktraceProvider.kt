package io.justtrack.crashes

internal interface StacktraceProvider {
    fun provideMainStacktrace(): Array<StackTraceElement>
    fun provideAllStacktrace(): List<ThreadStacktrace>
}
