package io.justtrack

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal class NetworkErrorLogger {
    fun logException(logger: Logger, exception: Throwable, message: String) {
        if (isNetworkException(exception)) {
            val field = LoggerFieldsBuilder().with("exception", exception.message.toString())
            logger.info(message, field)
        } else {
            logger.warn(message, exception)
        }
    }

    private fun isNetworkException(exception: Throwable): Boolean {
        val cause = exception.cause ?: exception
        return cause is UnknownHostException ||
            cause is SocketTimeoutException ||
            cause is ConnectException ||
            cause is SSLException ||
            cause is InterruptedIOException
    }
}
