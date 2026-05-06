package io.justtrack.network

import io.justtrack.log.Logger
import io.justtrack.okhttp.Interceptor
import io.justtrack.okhttp.Response
import io.justtrack.okio.Buffer
import java.io.IOException

internal class LoggingInterceptor internal constructor(val logger: Logger?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Log request
        val requestLog = buildString {
            appendLine("--> ${request.method} ${request.url}")
            request.headers.forEach { (name, value) ->
                appendLine("$name: $value")
            }
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                appendLine("Body: ${buffer.readString(charset)}")
            }
            append("--> END ${request.method}")
        }
        logger?.info(requestLog)

        // Proceed with request
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            logger?.info("<-- FAILED ${request.method} ${request.url}: $e")
            throw e
        }

        // Log response
        val responseBody = response.body
        val source = responseBody?.source()
        source?.request(Long.MAX_VALUE)
        val buffer = source?.buffer?.clone()
        val charset = responseBody?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val bodyString = buffer?.readString(charset)

        val responseLog = buildString {
            appendLine("<-- ${response.code} ${response.message} ${response.request.url}")
            response.headers.forEach { (name, value) ->
                appendLine("$name: $value")
            }
            if (bodyString != null) {
                appendLine("Body: $bodyString")
            }
            append("<-- END HTTP")
        }
        logger?.info(responseLog)

        return response
    }
}
