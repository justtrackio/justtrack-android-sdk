package io.justtrack

import io.justtrack.okhttp.Headers
import io.justtrack.okhttp.MediaType.Companion.toMediaType
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class HttpClientContentTypeTest {
    private var server = MockWebServer()

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun postRequest_sendsContentTypeHeaderOnTheWire(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val json = JSONObject().apply { put("key", "value") }
        val body = json.toString().toRequestBody(mediaType)

        val headers = Headers.Builder().apply {
            add("X-CLIENT-ID", "test.app")
            add("X-CLIENT-TOKEN", "test-token")
        }.build()

        val request = Request.Builder()
            .url(server.url("/test").toString())
            .headers(headers)
            .post(body)
            .build()

        val executor = OkHttpClientExecutor(consoleLogger = null)
        executor.sendRequest(request)

        val recordedRequest = getRequest()
        val contentType = recordedRequest.getHeader("Content-Type")

        assertNotNull("Content-Type header must be present on the wire", contentType)
        assertEquals(
            "application/json; charset=utf-8",
            contentType,
        )
    }

    @Test
    fun postRequest_withEmptyBody_sendsContentTypeHeaderOnTheWire(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = ByteArray(0).toRequestBody(mediaType)

        val headers = Headers.Builder().apply {
            add("X-CLIENT-ID", "test.app")
        }.build()

        val request = Request.Builder()
            .url(server.url("/test").toString())
            .headers(headers)
            .post(body)
            .build()

        val executor = OkHttpClientExecutor(consoleLogger = null)
        executor.sendRequest(request)

        val recordedRequest = getRequest()
        val contentType = recordedRequest.getHeader("Content-Type")

        assertNotNull("Content-Type header must be present even with empty body", contentType)
        assertEquals(
            "application/json; charset=utf-8",
            contentType,
        )
    }

    @Test
    fun postRequest_withoutMediaType_doesNotSendContentTypeHeader(): Unit = runBlocking {
        // This demonstrates the old broken behavior
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val body = """{"key":"value"}""".toRequestBody() // no media type - the old bug

        val request = Request.Builder()
            .url(server.url("/test").toString())
            .post(body)
            .build()

        val executor = OkHttpClientExecutor(consoleLogger = null)
        executor.sendRequest(request)

        val recordedRequest = getRequest()
        val contentType = recordedRequest.getHeader("Content-Type")

        // Without media type, no Content-Type header is sent - this is the bug
        assertEquals(null, contentType)
    }

    private suspend fun getRequest(): RecordedRequest = withContext(Dispatchers.IO) {
        server.takeRequest(10, TimeUnit.SECONDS)
            ?: throw AssertionError("No request was received")
    }
}
