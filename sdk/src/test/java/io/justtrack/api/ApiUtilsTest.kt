package io.justtrack.api

import io.justtrack.okhttp.Headers
import io.justtrack.okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiUtilsTest {

    private val headers = Headers.Builder().add("X-Test", "1").build()

    // region createPostRequest

    @Test
    fun createPostRequest_serialisesJsonBodyAndSetsMethodToPost() {
        val body = JSONObject("""{"foo":"bar"}""")
        val request = createPostRequest("https://example.com/post", headers, body)

        assertEquals("POST", request.method)
        assertEquals("https://example.com/post", request.url.toString())
        assertEquals("1", request.header("X-Test"))
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals("""{"foo":"bar"}""", buffer.readUtf8())
    }

    @Test
    fun createPostRequest_emitsEmptyBodyWhenJsonIsNull() {
        val request = createPostRequest("https://example.com/post", headers, null)

        assertEquals("POST", request.method)
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals("", buffer.readUtf8())
        assertEquals(0L, request.body!!.contentLength())
    }

    // endregion

    // region createGetRequest

    @Test
    fun createGetRequest_withoutParamsLeavesUrlUnchanged() {
        val request = createGetRequest("https://example.com/get", headers)

        assertEquals("GET", request.method)
        assertEquals("https://example.com/get", request.url.toString())
        assertEquals("1", request.header("X-Test"))
    }

    @Test
    fun createGetRequest_withParamsAppendsThemAsQueryString() {
        val request = createGetRequest(
            url = "https://example.com/get",
            headers = headers,
            params = linkedMapOf("a" to "1", "b" to "two words"),
        )

        assertEquals("GET", request.method)
        val urlString = request.url.toString()
        assertTrue("URL must contain a=1, was: $urlString", urlString.contains("a=1"))
        // okhttp percent-encodes the space.
        assertTrue("URL must encode space, was: $urlString", urlString.contains("b=two%20words"))
    }

    @Test
    fun createGetRequest_throwsIllegalArgumentForInvalidUrl() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            createGetRequest("not a real url", headers)
        }
        assertNotNull(ex.message)
        assertTrue("Exception must mention invalid url, was: ${ex.message}", ex.message!!.contains("Invalid url"))
    }

    // endregion
}
