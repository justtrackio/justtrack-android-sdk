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

class PrivacyApiTest {

    private val anonymizeUrl = "https://api.justtrack.io/v0/anonymize"

    private val environment = mock<Environment>().apply {
        whenever(getUrl(any())).thenAnswer { invocation ->
            when (invocation.getArgument<Environment.Route>(0)) {
                Environment.Route.ANONYMIZE -> anonymizeUrl
                else -> error("Unexpected route: ${invocation.getArgument<Environment.Route>(0)}")
            }
        }
    }
    private val logger = TestApiLogger()
    private val headerProvider = TestHeaderProvider()

    private val emptyBody = object : JSONEncodable {
        override fun toJSON(formatter: Formatter): JSONObject = JSONObject()
    }

    @Test
    fun anonymizeUser_success_postsToAnonymizeUrlWithExpectedRequestName() = runBlocking {
        val responseBody = JSONObject("""{"ok":true}""")
        val capturing = CapturingHttpClient(Result.success(responseBody))
        val api = PrivacyApiImpl(capturing, headerProvider, environment, logger)

        val result = api.anonymizeUser(advertiserId = "adv", uuid = "uuid", installId = "iid", body = emptyBody)

        assertTrue(result.isSuccess)
        assertEquals(responseBody, result.getOrNull())
        assertEquals(PrivacyApiImpl.SEND_ANONYMIZE_REQUEST_NAME, capturing.lastRequestName)
        assertEquals(anonymizeUrl, capturing.lastRequest?.url?.toString())
        assertEquals("POST", capturing.lastRequest?.method)
    }

    @Test
    fun anonymizeUser_supportsAllNullIdentifiers() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = PrivacyApiImpl(capturing, headerProvider, environment, logger)

        val result = api.anonymizeUser(advertiserId = null, uuid = null, installId = null, body = emptyBody)

        assertTrue(result.isSuccess)
    }

    @Test
    fun anonymizeUser_returnsFailureWhenHttpClientFails() = runBlocking {
        val capturing = CapturingHttpClient(Result.failure(Exception("network error")))
        val api = PrivacyApiImpl(capturing, headerProvider, environment, logger)

        val result = api.anonymizeUser(advertiserId = "adv", uuid = "uuid", installId = "iid", body = emptyBody)

        assertTrue(result.isFailure)
        assertEquals("network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun anonymizeUser_returnsFailureWhenBodyEncodingThrows() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = PrivacyApiImpl(capturing, headerProvider, environment, logger)
        val throwingBody = object : JSONEncodable {
            override fun toJSON(formatter: Formatter): JSONObject = error("encode failed")
        }

        val result = api.anonymizeUser(advertiserId = "adv", uuid = "uuid", installId = "iid", body = throwingBody)

        assertTrue(result.isFailure)
    }

    @Test
    fun companion_requestName_isStable() {
        assertEquals("SendAnonymize", PrivacyApiImpl.SEND_ANONYMIZE_REQUEST_NAME)
    }

    private class CapturingHttpClient(private val response: Result<JSONObject>) : HttpClient {
        var lastRequest: Request? = null
        var lastRequestName: String? = null

        override suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
            lastRequest = request
            lastRequestName = requestName
            delay(1)
            return response
        }

        override suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<HttpClient.JsonHttpResponse> =
            response.map { HttpClient.JsonHttpResponse(it, null) }
    }
}
