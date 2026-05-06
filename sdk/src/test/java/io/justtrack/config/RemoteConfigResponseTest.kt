package io.justtrack.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.justtrack.okhttp.Headers

class RemoteConfigResponseTest {
    @Test
    fun retryAfterSeconds_parsesCaseInsensitiveHeader() {
        val headers = Headers.Builder().add("retry-after", " 120 ").build()
        val response = RemoteConfigResponse(JSONObject(), headers)

        assertEquals(120, response.retryAfterSeconds)
    }

    @Test
    fun retryAfterSeconds_returnsNullForMissingHeader() {
        val response = RemoteConfigResponse(JSONObject(), Headers.Builder().build())

        assertNull(response.retryAfterSeconds)
    }

    @Test
    fun retryAfterSeconds_returnsNullForInvalidValues() {
        val negative = RemoteConfigResponse(
            JSONObject(),
            Headers.Builder().add("Retry-After", "-2").build(),
        )
        val nonNumeric = RemoteConfigResponse(
            JSONObject(),
            Headers.Builder().add("Retry-After", "abc").build(),
        )

        assertNull(negative.retryAfterSeconds)
        assertNull(nonNumeric.retryAfterSeconds)
    }
}
