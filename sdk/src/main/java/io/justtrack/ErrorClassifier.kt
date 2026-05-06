package io.justtrack

internal interface ErrorClassifier {
    fun unrecoverable(exception: Throwable): Boolean

    fun waitTime(exception: Throwable): Double {
        return 0.0
    }
}
