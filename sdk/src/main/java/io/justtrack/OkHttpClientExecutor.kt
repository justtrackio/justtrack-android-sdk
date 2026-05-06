package io.justtrack

import io.justtrack.log.Logger
import io.justtrack.network.LoggingInterceptor
import io.justtrack.okhttp.Call
import io.justtrack.okhttp.Callback
import io.justtrack.okhttp.OkHttpClient
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OkHttpClientExecutor internal constructor(
    private val readTimeout: Long = DEFAULT_READ_TIMEOUT,
    private val writeTimeout: Long = DEFAULT_WRITE_TIMEOUT,
    private val connectionTimeout: Long = DEFAULT_CONNECTION_TIMEOUT,
    private val consoleLogger: Logger?,
) {

    private var client: OkHttpClient = OkHttpClient.Builder().apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(LoggingInterceptor(consoleLogger))
        }
        addInterceptor(GzipRequestInterceptor())
        readTimeout(readTimeout, TimeUnit.MILLISECONDS)
        writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
        connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
    }.build()

    internal suspend fun sendRequest(request: Request): Result<Response> = try {
        val response = client
            .newCall(request)
            .await()

        Result.success(response)
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    internal suspend fun Call.await() = suspendCancellableCoroutine { continuation ->
        this.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            },
        )
    }

    companion object {
        private const val DEFAULT_READ_TIMEOUT = 30_000L
        private const val DEFAULT_WRITE_TIMEOUT = 30_000L
        private const val DEFAULT_CONNECTION_TIMEOUT = 30_000L
    }
}
