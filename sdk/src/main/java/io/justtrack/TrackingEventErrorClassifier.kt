package io.justtrack

import org.json.JSONException

internal class TrackingEventErrorClassifier : ErrorClassifier {
    override fun unrecoverable(exception: Throwable): Boolean {
        if (exception is BadResponseException) {
            // the backend seems to be having problems. Better not to overload it with too many requests,
            // we will retry later anyway
            return true
        }

        if (exception is NetworkProblemException) {
            // network is down? This is the prime reason why we retry
            return false
        }

        if (exception is JSONException) {
            // no point in retrying if we failed because of an incompatible backend response
            return true
        }

        val cause = exception.cause
        if (cause != null) {
            return unrecoverable(cause)
        }

        return false
    }

    companion object {
        @JvmStatic @JvmSynthetic
        val instance: ErrorClassifier = TrackingEventErrorClassifier()
    }
}
