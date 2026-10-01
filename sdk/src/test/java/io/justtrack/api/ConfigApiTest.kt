package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.config.RemoteConfigQueryParams
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

class ConfigApiTest {

    private val environment = mock<Environment>().apply {
        whenever(getUrl(any())).thenAnswer { invocation ->
            when (invocation.getArgument<Environment.Route>(0)) {
                Environment.Route.REMOTE_CONFIG -> "https://sdk-api.justtrack.io/ab-test/v0/assignments"
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

    // region fetchRemoteConfig

    @Test
    fun fetchRemoteConfig_returnsSuccessWhenHttpClientSucceeds() = runBlocking {
        val responseBody = JSONObject("""{"assignments":[]}""")
        val httpClient = SuccessJsonHttpClient(responseBody)
        val api = ConfigApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.fetchRemoteConfig(
            queryParams = testQueryParams(),
            advertiserId = "adv-id",
            uuid = "uuid",
            installId = "install-id",
        )

        assertTrue(result.isSuccess)
        assertEquals(responseBody, result.getOrNull()?.body)
    }

    @Test
    fun fetchRemoteConfig_returnsFailureWhenHttpClientFails() = runBlocking {
        val httpClient = FailingHttpClient()
        val api = ConfigApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.fetchRemoteConfig(
            queryParams = testQueryParams(),
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun fetchRemoteConfig_usesCorrectRequestName() = runBlocking {
        val capturingClient = CapturingJsonHttpClient(JSONObject())
        val api = ConfigApiImpl(capturingClient, headerProvider, environment, logger)

        api.fetchRemoteConfig(
            queryParams = testQueryParams(),
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        assertEquals(ConfigApiImpl.GET_REMOTE_CONFIG_REQUEST_NAME, capturingClient.lastRequestName)
    }

    @Test
    fun fetchRemoteConfig_usesCorrectUrl() = runBlocking {
        val capturingClient = CapturingJsonHttpClient(JSONObject())
        val api = ConfigApiImpl(capturingClient, headerProvider, environment, logger)

        api.fetchRemoteConfig(
            queryParams = testQueryParams(),
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        val expectedUrl = environment.getUrl(Environment.Route.REMOTE_CONFIG)
        assertTrue(capturingClient.lastRequest?.url?.toString()?.startsWith(expectedUrl) == true)
    }

    // endregion

    // region activateExperiments

    @Test
    fun activateExperiments_returnsSuccessWhenHttpClientSucceeds() = runBlocking {
        val responseBody = JSONObject()
        val httpClient = SuccessHttpClient(responseBody)
        val api = ConfigApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.activateExperiments(
            body = emptyJSONEncodable,
            advertiserId = "adv-id",
            uuid = "uuid",
            installId = "install-id",
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun activateExperiments_returnsFailureWhenHttpClientFails() = runBlocking {
        val httpClient = FailingHttpClient()
        val api = ConfigApiImpl(httpClient, headerProvider, environment, logger)

        val result = api.activateExperiments(
            body = emptyJSONEncodable,
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun activateExperiments_usesCorrectRequestName() = runBlocking {
        val capturingClient = CapturingHttpClient(JSONObject())
        val api = ConfigApiImpl(capturingClient, headerProvider, environment, logger)

        api.activateExperiments(
            body = emptyJSONEncodable,
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        assertEquals(ConfigApiImpl.ACTIVATE_EXPERIMENTS, capturingClient.lastRequestName)
    }

    @Test
    fun activateExperiments_usesCorrectUrl() = runBlocking {
        val capturingClient = CapturingHttpClient(JSONObject())
        val api = ConfigApiImpl(capturingClient, headerProvider, environment, logger)

        api.activateExperiments(
            body = emptyJSONEncodable,
            advertiserId = null,
            uuid = null,
            installId = null,
        )

        val expectedUrl = environment.getUrl(Environment.Route.REMOTE_CONFIG)
        assertEquals(expectedUrl, capturingClient.lastRequest?.url?.toString())
    }

    // endregion

    // region helpers

    private fun testQueryParams() = RemoteConfigQueryParams(
        installInstanceId = "install-id",
        osVersion = "13",
        appVersionCode = "100",
        appVersionName = "1.0.0",
        sdkVersionMajor = "7",
        sdkVersionMinor = "0",
        sdkVersionPatch = "0",
        sdkVersionName = "7.0.0",
        sdkVersionPlatform = "android",
        sdkVersionWrapper = null,
        deviceType = "phone",
        deviceModel = "test-model",
        countryIso2 = null,
        deviceTimestamp = 0L,
        attributionTimestamp = null,
        firstSdkInitTimestamp = null,
        installTimestamp = null,
    )

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

    private class SuccessJsonHttpClient(private val body: JSONObject) : HttpClient {
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

    private class CapturingJsonHttpClient(private val body: JSONObject) : HttpClient {
        var lastRequest: Request? = null
        var lastRequestName: String? = null

        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
            delay(1)
            return Result.success(body)
        }

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> {
            lastRequest = request
            lastRequestName = requestName
            delay(1)
            return Result.success(HttpClient.JsonHttpResponse(body, null))
        }
    }
}
