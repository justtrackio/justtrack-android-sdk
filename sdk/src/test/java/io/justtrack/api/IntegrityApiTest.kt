package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.log.Logger
import io.justtrack.okhttp.Request
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntegrityApiTest {

    private val environment = mock<Environment>().apply {
        whenever(getUrl(any())).thenAnswer { invocation ->
            when (invocation.getArgument<Environment.Route>(0)) {
                Environment.Route.REPORT_INTEGRITY -> "https://fraud-detector.justtrack.io/v0/integrity"
                else -> error("Unexpected route")
            }
        }
    }
    private val logger = TestApiLogger()
    private val headerProvider = TestHeaderProvider()
    private val emptyJSONEncodable = object : JSONEncodable {
        override fun toJSON(formatter: Formatter): JSONObject {
            return JSONObject()
        }
    }

    @Test
    fun reportIntegrity_returnsSuccessWhenHttpClientSucceeds() = runBlocking {
        val responseBody = JSONObject()
        val httpClient = SuccessHttpClient(responseBody)
        val api = IntegrityApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.reportIntegrity(
            body = object : JSONEncodable {
                override fun toJSON(formatter: Formatter): JSONObject {
                    return JSONObject("""{"token":"abc"}""")
                }
            },
            installId = "install-id",
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun reportIntegrity_returnsFailureWhenHttpClientFails() = runBlocking {
        val httpClient = FailingHttpClient()
        val api = IntegrityApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.reportIntegrity(
            body = object : JSONEncodable {
                override fun toJSON(formatter: Formatter): JSONObject {
                    return JSONObject("""{"token":"abc"}""")
                }
            },
            installId = "install-id",
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun reportIntegrity_usesCorrectRequestName() = runBlocking {
        val capturingClient = CapturingHttpClient(JSONObject())
        val api = IntegrityApiImpl(capturingClient, headerProvider, environment, logger)

        api.reportIntegrity(
            body = emptyJSONEncodable,
            installId = "install-id",
        )

        assertEquals(IntegrityApiImpl.SEND_INTEGRITY_TOKEN_NAME, capturingClient.lastRequestName)
    }

    @Test
    fun reportIntegrity_usesCorrectUrl() = runBlocking {
        val capturingClient = CapturingHttpClient(JSONObject())
        val api = IntegrityApiImpl(capturingClient, headerProvider, environment, logger)

        api.reportIntegrity(
            body = emptyJSONEncodable,
            installId = "install-id",
        )

        val expectedUrl = environment.getUrl(Environment.Route.REPORT_INTEGRITY)
        assertEquals(expectedUrl, capturingClient.lastRequest?.url?.toString())
    }

    // region helpers

    private class SuccessHttpClient(private val body: JSONObject) : HttpClient {
        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> = Result.success(body)

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> =
            Result.success(HttpClient.JsonHttpResponse(body, null))
    }

    private class FailingHttpClient : HttpClient {
        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> =
            Result.failure(Exception("network error"))

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> =
            Result.failure(Exception("network error"))
    }

    private class CapturingHttpClient(private val body: JSONObject) : HttpClient {
        var lastRequest: Request? = null
        var lastRequestName: String? = null

        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
            lastRequest = request
            lastRequestName = requestName
            return Result.success(body)
        }

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> =
            Result.success(HttpClient.JsonHttpResponse(body, null))
    }

    // endregion
}
