package io.justtrack

import java.util.concurrent.RejectedExecutionException

internal fun interface RejectedExecutionExceptionHandler {
    fun handleRejectedExecution(exception: RejectedExecutionException)
}
