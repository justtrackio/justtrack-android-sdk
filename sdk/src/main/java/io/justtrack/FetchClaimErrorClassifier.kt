package io.justtrack

import android.system.ErrnoException
import android.system.OsConstants
import java.net.ConnectException
import java.net.UnknownHostException

internal class FetchClaimErrorClassifier : ErrorClassifier {

    override fun unrecoverable(exception: Throwable): Boolean {
        if (isUnreachableException(exception)) {
            // no need to retry requests for a protocol we don't support
            return true
        }

        return TrackingEventErrorClassifier.instance.unrecoverable(exception)
    }

    companion object {
        private const val CRITICAL_ERROR_CODE = 401

        @JvmStatic @JvmSynthetic
        val instance: ErrorClassifier = FetchClaimErrorClassifier()

        @JvmStatic
        fun isUnreachableException(e: Throwable?): Boolean {
            if (e == null) {
                return false
            }

            if (e is ErrnoException) {
                return e.errno == OsConstants.ENETUNREACH
            }

            if (e is UnknownHostException) {
                return true
            }

            if (e is ConnectException) {
                return true
            }

            return isUnreachableException(e.cause)
        }

        /**
         * Currently only 401 will be printed to logcat, this is most likely due to wrong token.
         * @param e the throwable received.
         * @return whether the exception is critical and should be printed or not.
         */
        fun isCriticalException(e: Throwable?): Boolean {
            if (e is BadResponseException) {
                return e.responseCode == CRITICAL_ERROR_CODE
            }

            return false
        }
    }
}
