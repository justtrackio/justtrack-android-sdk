package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.Response
import org.json.JSONException
import org.json.JSONObject

internal class HttpClientImpl @VisibleForTesting internal constructor(
    private val deviceInfo: DeviceInfo,
    private val client: OkHttpClientExecutor,
) : HttpClient {

    constructor(
        deviceInfo: DeviceInfo,
        consoleLogger: Logger?,
    ) : this(
        deviceInfo = deviceInfo,
        client = OkHttpClientExecutor(consoleLogger = consoleLogger),
    )

    override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
        return executeAsyncJsonRequest(request, requestName, logger).map { it.body }
    }

    override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> {
        val connectionType = deviceInfo.getConnectionType()

        return client.sendRequest(request).fold(
            onSuccess = { response ->
                onExecuteSuccess(response)
            },
            onFailure = { e ->
                val dimensions: LoggerFields =
                    LoggerFieldsBuilder()
                        .with("Request", requestName)
                        .with("Network", connectionType.toString())
                        .with("Reason", "NetworkProblem")
                logger.publishMetric(REQUEST_FAILURES_METRIC, 1.0, dimensions)
                Result.failure(NetworkProblemException(e))
            },
        )
    }

    private fun onExecuteSuccess(response: Response): Result<HttpClient.JsonHttpResponse> {
        return try {
            val responseData = response.body?.string()
            when {
                !response.isSuccessful ->
                    Result.failure(
                        BadResponseException(
                            BadResponseException.formatBadResponseStatusMessage(
                                response.code,
                                response.message,
                                responseData,
                            ),
                            response.code,
                            responseData,
                        ),
                    )

                responseData == null ->
                    Result.failure(
                        BadResponseException(
                            "No response body was returned",
                            response.code,
                        ),
                    )

                else -> {
                    try {
                        Result.success(
                            HttpClient.JsonHttpResponse(
                                JSONObject(responseData),
                                response.headers,
                            ),
                        )
                    } catch (e: JSONException) {
                        Result.failure(
                            BadResponseException(
                                "Failed to parse response body as JSON",
                                response.code,
                                responseData,
                                e,
                            ),
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Result.failure(RuntimeException("Failed to handle response", e))
        }
    }

    companion object {
        const val SIGN_IPV4_REQUEST_NAME = "SignIPv4"
        const val SIGN_IPV6_REQUEST_NAME = "SignIPv6"
        private val REQUEST_FAILURES_METRIC = Metric("RequestFailures")
    }
}
