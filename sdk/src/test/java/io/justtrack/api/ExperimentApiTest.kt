package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.log.Logger
import io.justtrack.okhttp.Request
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExperimentApiTest {

    private val environment = mock<Environment>().apply {
        whenever(getUrl(any())).thenAnswer { invocation ->
            when (invocation.getArgument<Environment.Route>(0)) {
                Environment.Route.AB_TEST_ASSIGNMENT -> "https://sdk-api.justtrack.io/ab-test/v0/assignment"
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
    fun setExperimentVariant_returnsSuccessWhenHttpClientSucceeds() = runBlocking {
        val responseBody = JSONObject()
        val httpClient = SuccessHttpClient(responseBody)
        val api = ExperimentApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.setExperimentVariant(
            body = emptyJSONEncodable,
            advertiserId = "adv-id",
            uuid = "uuid",
            installId = "install-id",
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun setExperimentVariant_returnsFailureWhenHttpClientFails() = runBlocking {
        val httpClient = FailingHttpClient()
        val api = ExperimentApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.setExperimentVariant(
            body = emptyJSONEncodable,
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun setExperimentVariant_usesCorrectRequestName() = runBlocking {
        val capturingClient = CapturingHttpClient(JSONObject())
        val api = ExperimentApiImpl(capturingClient, headerProvider, environment, logger)

        api.setExperimentVariant(
            body = emptyJSONEncodable,
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        assertEquals(ExperimentApiImpl.SET_EXPERIMENT_VARIANT_REQUEST_NAME, capturingClient.lastRequestName)
    }

    @Test
    fun setExperimentVariant_usesCorrectUrl() = runBlocking {
        val capturingClient = CapturingHttpClient(JSONObject())
        val api = ExperimentApiImpl(capturingClient, headerProvider, environment, logger)

        api.setExperimentVariant(
            body = emptyJSONEncodable,
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        val expectedUrl = environment.getUrl(Environment.Route.AB_TEST_ASSIGNMENT)
        assertEquals(expectedUrl, capturingClient.lastRequest?.url?.toString())
    }

    // region helpers

    private class SuccessHttpClient(private val body: JSONObject) : HttpClient {
        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
            delay(1)
            return Result.success(body)
        }

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> {
            delay(1)
            return Result.success(HttpClient.JsonHttpResponse(body, null))
        }
    }

    private class FailingHttpClient : HttpClient {
        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
            delay(1)
            return Result.failure(Exception("network error"))
        }

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> {
            delay(1)
            return Result.failure(Exception("network error"))
        }
    }

    private class CapturingHttpClient(private val body: JSONObject) : HttpClient {
        var lastRequest: Request? = null
        var lastRequestName: String? = null

        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
            lastRequest = request
            lastRequestName = requestName
            delay(1)
            return Result.success(body)
        }

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> {
            delay(1)
            return Result.success(HttpClient.JsonHttpResponse(body, null))
        }
    }

    // endregion
}
