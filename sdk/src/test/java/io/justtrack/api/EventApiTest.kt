package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.HttpClient
import io.justtrack.dtos.DTOAppEvent
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

class EventApiTest {

    private val trackEventUrl = "https://api.justtrack.io/v0/track-event"

    private val environment = mock<Environment>().apply {
        whenever(getUrl(any())).thenAnswer { invocation ->
            when (invocation.getArgument<Environment.Route>(0)) {
                Environment.Route.TRACK_EVENT -> trackEventUrl
                else -> error("Unexpected route: ${invocation.getArgument<Environment.Route>(0)}")
            }
        }
    }
    private val logger = TestApiLogger()
    private val headerProvider = TestHeaderProvider()

    private fun appEvent(json: JSONObject = JSONObject()): DTOAppEvent = mock<DTOAppEvent>().apply {
        whenever(toJSON(any())).thenReturn(json)
    }

    @Test
    fun sendUserEvents_success_postsToTrackEventUrlWithExpectedRequestName() = runBlocking {
        val responseBody = JSONObject("""{"ok":true}""")
        val capturing = CapturingHttpClient(Result.success(responseBody))
        val api = EventApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendUserEvents(
            body = appEvent(),
            advertiserId = "adv",
            uuid = "uuid",
            installId = "iid",
        )

        assertTrue(result.isSuccess)
        assertEquals(responseBody, result.getOrNull())
        assertEquals(EventApiImpl.SEND_USER_EVENTS_REQUEST_NAME, capturing.lastRequestName)
        assertEquals(trackEventUrl, capturing.lastRequest?.url?.toString())
        assertEquals("POST", capturing.lastRequest?.method)
    }

    @Test
    fun sendUserEvents_supportsNullAdvertiserId() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = EventApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendUserEvents(
            body = appEvent(),
            advertiserId = null,
            uuid = "uuid",
            installId = "iid",
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun sendUserEvents_returnsFailureWhenHttpClientFails() = runBlocking {
        val capturing = CapturingHttpClient(Result.failure(Exception("network error")))
        val api = EventApiImpl(capturing, headerProvider, environment, logger)

        val result = api.sendUserEvents(
            body = appEvent(),
            advertiserId = "adv",
            uuid = "uuid",
            installId = "iid",
        )

        assertTrue(result.isFailure)
        assertEquals("network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun sendUserEvents_returnsFailureWhenBodyEncodingThrows() = runBlocking {
        val capturing = CapturingHttpClient(Result.success(JSONObject()))
        val api = EventApiImpl(capturing, headerProvider, environment, logger)
        val throwingBody = mock<DTOAppEvent>().apply {
            whenever(toJSON(any())).thenThrow(RuntimeException("encode failed"))
        }

        val result = api.sendUserEvents(
            body = throwingBody,
            advertiserId = "adv",
            uuid = "uuid",
            installId = "iid",
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun companion_requestName_isStable() {
        assertEquals("SendUserEvents", EventApiImpl.SEND_USER_EVENTS_REQUEST_NAME)
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
