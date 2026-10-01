package io.justtrack.network

import io.justtrack.log.Logger
import io.justtrack.okhttp.Interceptor
import io.justtrack.okhttp.MediaType.Companion.toMediaType
import io.justtrack.okhttp.Protocol
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.RequestBody.Companion.toRequestBody
import io.justtrack.okhttp.Response
import io.justtrack.okhttp.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.IOException

class LoggingInterceptorTest {

    private val logger = mock<Logger>()

    private fun chainOf(request: Request, response: Response): Interceptor.Chain = mock<Interceptor.Chain>().also {
        whenever(it.request()).thenReturn(request)
        whenever(it.proceed(any())).thenReturn(response)
    }

    private fun newResponse(
        request: Request,
        code: Int = 200,
        message: String = "OK",
        body: io.justtrack.okhttp.ResponseBody? = "ok".toResponseBody("text/plain".toMediaType()),
        headers: Map<String, String> = mapOf("X-Resp" to "r1"),
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        if (body != null) builder.body(body) else builder.body(null)
        return builder.build()
    }

    @Test
    fun `logs GET request without body and response with body`() {
        val request = Request.Builder()
            .url("https://example.com/path?q=1")
            .get()
            .addHeader("X-Req", "v1")
            .addHeader("Accept", "application/json")
            .build()
        val response = newResponse(request)

        val result = LoggingInterceptor(logger).intercept(chainOf(request, response))

        assertSame(response, result)
        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())

        val req = captor.firstValue
        assertTrue(req, req.startsWith("--> GET https://example.com/path?q=1"))
        assertTrue(req, req.contains("X-Req: v1"))
        assertTrue(req, req.contains("Accept: application/json"))
        assertTrue(req, req.endsWith("--> END GET"))
        // No body line for GET requests
        assertTrue(req, !req.contains("Request Body:"))

        val resp = captor.secondValue
        assertTrue(resp, resp.startsWith("<-- 200 OK https://example.com/path?q=1"))
        assertTrue(resp, resp.contains("X-Resp: r1"))
        assertTrue(resp, resp.contains("Response Body: ok"))
        assertTrue(resp, resp.endsWith("<-- END HTTP"))
    }

    @Test
    fun `logs request body with content type that has no charset using UTF-8 default`() {
        // application/octet-stream has no charset parameter, so contentType()?.charset() returns null
        // and the elvis fallback to Charsets.UTF_8 fires (covers a separate branch from null contentType).
        val request = Request.Builder()
            .url("https://example.com/post")
            .post("binary-text".toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val response = newResponse(
            request,
            // Same case for the response body branch (line 44).
            body = "resp-no-cs".toResponseBody("application/octet-stream".toMediaType()),
        )

        LoggingInterceptor(logger).intercept(chainOf(request, response))

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        assertTrue(captor.firstValue, captor.firstValue.contains("Request Body: binary-text"))
        assertTrue(captor.secondValue, captor.secondValue.contains("Response Body: resp-no-cs"))
    }

    @Test
    fun `logs POST request body using content type charset`() {
        val request = Request.Builder()
            .url("https://example.com/post")
            .post("""{"k":"v"}""".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val response = newResponse(request)

        LoggingInterceptor(logger).intercept(chainOf(request, response))

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        val req = captor.firstValue
        assertTrue(req, req.startsWith("--> POST https://example.com/post"))
        assertTrue(req, req.contains("""Request Body: {"k":"v"}"""))
        assertTrue(req, req.endsWith("--> END POST"))
    }

    @Test
    fun `logs POST request body with no content type using UTF-8 default`() {
        // Passing null MediaType exercises the `?: Charsets.UTF_8` fallback.
        val request = Request.Builder()
            .url("https://example.com/post")
            .post("hello-utf8".toRequestBody(contentType = null))
            .build()
        val response = newResponse(request)

        LoggingInterceptor(logger).intercept(chainOf(request, response))

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        assertTrue(captor.firstValue, captor.firstValue.contains("Request Body: hello-utf8"))
    }

    @Test
    fun `logs response with content type charset and without body`() {
        val request = Request.Builder().url("https://example.com/").get().build()
        val response = newResponse(
            request,
            body = "résumé".toResponseBody("text/plain; charset=utf-8".toMediaType()),
        )

        LoggingInterceptor(logger).intercept(chainOf(request, response))

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        assertTrue(captor.secondValue, captor.secondValue.contains("Response Body: résumé"))
    }

    @Test
    fun `logs response body with no content type using UTF-8 default`() {
        val request = Request.Builder().url("https://example.com/").get().build()
        val response = newResponse(
            request,
            body = "no-ct".toResponseBody(contentType = null),
        )

        LoggingInterceptor(logger).intercept(chainOf(request, response))

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        assertTrue(captor.secondValue, captor.secondValue.contains("Response Body: no-ct"))
    }

    @Test
    fun `omits response body line when response has no body`() {
        val request = Request.Builder().url("https://example.com/").get().build()
        val response = newResponse(request, code = 204, message = "No Content", body = null)

        LoggingInterceptor(logger).intercept(chainOf(request, response))

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        val resp = captor.secondValue
        assertTrue(resp, resp.startsWith("<-- 204 No Content https://example.com/"))
        assertTrue(resp, !resp.contains("Response Body:"))
        assertTrue(resp, resp.endsWith("<-- END HTTP"))
    }

    @Test
    fun `logs failure and rethrows when chain proceed throws IOException`() {
        val request = Request.Builder()
            .url("https://example.com/boom")
            .post("x".toRequestBody("text/plain".toMediaType()))
            .build()
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        val cause = IOException("network down")
        whenever(chain.proceed(any())).thenThrow(cause)

        try {
            LoggingInterceptor(logger).intercept(chain)
            fail("Expected IOException to be rethrown")
        } catch (e: IOException) {
            assertSame(cause, e)
        }

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        // First log is the request log; second is the failure log.
        assertTrue(captor.firstValue, captor.firstValue.startsWith("--> POST https://example.com/boom"))
        val failure = captor.secondValue
        assertTrue(failure, failure.startsWith("<-- FAILED POST https://example.com/boom"))
        assertTrue(failure, failure.contains("network down"))
    }

    @Test
    fun `does nothing on logger when logger is null and still returns response`() {
        val request = Request.Builder()
            .url("https://example.com/")
            .post("payload".toRequestBody("text/plain".toMediaType()))
            .addHeader("X", "y")
            .build()
        val response = newResponse(request)

        val result = LoggingInterceptor(null).intercept(chainOf(request, response))

        assertSame(response, result)
        verifyNoInteractions(logger)
    }

    @Test(expected = IOException::class)
    fun `with null logger still rethrows IOException without logging`() {
        val request = Request.Builder().url("https://example.com/").get().build()
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(any())).thenThrow(IOException("nope"))

        try {
            LoggingInterceptor(null).intercept(chain)
        } finally {
            verify(logger, never()).info(any<String>())
        }
    }

    @Test
    fun `preserves response code and message in log line`() {
        val request = Request.Builder().url("https://example.com/x").get().build()
        val response = newResponse(request, code = 503, message = "Service Unavailable", body = null)

        LoggingInterceptor(logger).intercept(chainOf(request, response))

        val captor = argumentCaptor<String>()
        verify(logger, times(2)).info(captor.capture())
        assertEquals(true, captor.secondValue.startsWith("<-- 503 Service Unavailable https://example.com/x"))
    }

    @Test
    fun `exposes logger passed via constructor`() {
        // Covers the synthetic getter for the public `val logger` property.
        assertSame(logger, LoggingInterceptor(logger).logger)
        assertEquals(null, LoggingInterceptor(null).logger)
    }
}
