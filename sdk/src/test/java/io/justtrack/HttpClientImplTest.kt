package io.justtrack

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.okhttp.MediaType.Companion.toMediaType
import io.justtrack.okhttp.Protocol
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.Response
import io.justtrack.okhttp.ResponseBody
import io.justtrack.okhttp.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class HttpClientImplTest {

    private val deviceInfo = mock<DeviceInfo>()
    private val logger = mock<Logger>()
    private val executor = mock<OkHttpClientExecutor>()
    private lateinit var sut: HttpClientImpl

    private val request: Request = Request.Builder().url("https://example.com/x").get().build()

    @Before
    fun setUp() {
        whenever(deviceInfo.getConnectionType()).thenReturn(ConnectionType.WIFI)
        sut = HttpClientImpl(deviceInfo, executor)
    }

    private fun newResponse(
        code: Int = 200,
        message: String = "OK",
        body: ResponseBody? = """{"a":"b"}""".toResponseBody("application/json".toMediaType()),
        headers: Map<String, String> = mapOf("X-Resp" to "r1"),
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        builder.body(body)
        return builder.build()
    }

    @Test
    fun `executeAsyncJsonRequest returns success with parsed body and headers`() = runBlocking<Unit> {
        whenever(executor.sendRequest(any())).thenReturn(Result.success(newResponse()))

        val result = sut.executeAsyncJsonRequest(request, "MyRequest", logger)

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("b", response.body.getString("a"))
        assertNotNull(response.headers)
        assertEquals("r1", response.headers?.get("X-Resp"))
        verify(logger, never()).publishMetric(any(), any(), any())
    }

    @Test
    fun `executeAsyncRequest returns just the JSON body on success`() = runBlocking<Unit> {
        whenever(executor.sendRequest(any())).thenReturn(Result.success(newResponse()))

        val result = sut.executeAsyncRequest(request, "MyRequest", logger)

        assertTrue(result.isSuccess)
        assertEquals("b", result.getOrThrow().getString("a"))
    }

    @Test
    fun `non-success response yields BadResponseException with code and body`() = runBlocking<Unit> {
        val body = "server-error-body".toResponseBody("text/plain".toMediaType())
        whenever(executor.sendRequest(any())).thenReturn(Result.success(newResponse(code = 500, message = "ISE", body = body)))

        val result = sut.executeAsyncJsonRequest(request, "MyRequest", logger)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("expected BadResponseException, got $ex", ex is BadResponseException)
        val bad = ex as BadResponseException
        assertEquals(500, bad.responseCode)
        assertEquals("server-error-body", bad.body)
        assertTrue(bad.message!!.contains("500"))
    }

    @Test
    fun `non-success 401 response uses formatted error box message`() = runBlocking<Unit> {
        val body = "unauth".toResponseBody("text/plain".toMediaType())
        whenever(executor.sendRequest(any())).thenReturn(Result.success(newResponse(code = 401, message = "Unauthorized", body = body)))

        val result = sut.executeAsyncJsonRequest(request, "MyRequest", logger)

        val ex = result.exceptionOrNull() as BadResponseException
        assertEquals(401, ex.responseCode)
        assertTrue(ex.message!!.contains("API token"))
    }

    @Test
    fun `successful response with null body yields BadResponseException`() = runBlocking<Unit> {
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(204)
            .message("No Content")
            .body(null)
            .build()
        whenever(executor.sendRequest(any())).thenReturn(Result.success(response))

        val result = sut.executeAsyncJsonRequest(request, "MyRequest", logger)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as BadResponseException
        assertEquals(204, ex.responseCode)
        assertEquals("No response body was returned", ex.message)
        assertNull(ex.body)
    }

    @Test
    fun `success response with invalid JSON yields BadResponseException with cause`() = runBlocking<Unit> {
        val body = "not-json".toResponseBody("text/plain".toMediaType())
        whenever(executor.sendRequest(any())).thenReturn(Result.success(newResponse(body = body)))

        val result = sut.executeAsyncJsonRequest(request, "MyRequest", logger)

        val ex = result.exceptionOrNull() as BadResponseException
        assertEquals(200, ex.responseCode)
        assertEquals("not-json", ex.body)
        assertEquals("Failed to parse response body as JSON", ex.message)
        assertNotNull(ex.cause)
    }

    @Test
    fun `network failure publishes failure metric and returns NetworkProblemException`() = runBlocking<Unit> {
        val cause = IOException("boom")
        whenever(deviceInfo.getConnectionType()).thenReturn(ConnectionType.CELLULAR_4G)
        whenever(executor.sendRequest(any())).thenReturn(Result.failure(cause))

        val result = sut.executeAsyncJsonRequest(request, "MyRequest", logger)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("expected NetworkProblemException, got $ex", ex is NetworkProblemException)
        assertSame(cause, ex!!.cause)

        val metricCaptor = argumentCaptor<Metric>()
        val valueCaptor = argumentCaptor<Double>()
        val fieldsCaptor = argumentCaptor<LoggerFields>()
        verify(logger).publishMetric(metricCaptor.capture(), valueCaptor.capture(), fieldsCaptor.capture())
        assertEquals("RequestFailures", metricCaptor.firstValue.metric)
        assertEquals(1.0, valueCaptor.firstValue, 0.0)
        val fields = fieldsCaptor.firstValue.fields
        assertEquals("MyRequest", fields["Request"])
        assertEquals("cellular_4g", fields["Network"])
        assertEquals("NetworkProblem", fields["Reason"])
    }

    @Test
    fun `body string IOException is wrapped in RuntimeException`() = runBlocking<Unit> {
        val throwingBody = mock<ResponseBody>()
        whenever(throwingBody.string()).thenThrow(IOException("read failed"))
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(throwingBody)
            .build()
        whenever(executor.sendRequest(any())).thenReturn(Result.success(response))

        val result = sut.executeAsyncJsonRequest(request, "MyRequest", logger)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("expected RuntimeException, got $ex", ex is RuntimeException)
        assertEquals("Failed to handle response", ex!!.message)
        assertTrue(ex.cause is IOException)
    }

    @Test
    fun `passes request through to executor unchanged`() = runBlocking<Unit> {
        whenever(executor.sendRequest(any())).thenReturn(Result.success(newResponse()))

        sut.executeAsyncJsonRequest(request, "X", logger)

        verify(executor).sendRequest(eq(request))
    }

    @Test
    fun `secondary constructor builds working instance without injected executor`() {
        // Exercises the production constructor that instantiates its own OkHttpClientExecutor.
        // We only verify successful construction; no network calls are made on init.
        val client = HttpClientImpl(deviceInfo, null as Logger?)
        assertNotNull(client)
    }
}
