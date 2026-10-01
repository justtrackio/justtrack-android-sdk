package io.justtrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AttributionExceptionTest {

    @Test
    fun `wraps non-token-related cause with generic message`() {
        val cause = IOException("boom")

        val exception = AttributionException(cause)

        assertEquals("Attribution post request failed", exception.message)
        assertSame(cause, exception.cause)
        assertFalse(exception.wasApiTokenInvalid())
    }

    @Test
    fun `detects invalid api token from direct BadResponseException with 401`() {
        val cause = BadResponseException("unauthorized", 401)

        val exception = AttributionException(cause)

        assertTrue(exception.wasApiTokenInvalid())
        assertNotNull(exception.message)
        // The error-box message must mention the api-token hint.
        assertTrue(
            "expected api-token error box, got: ${exception.message}",
            exception.message!!.contains("Attribution can not be performed with an invalid API token."),
        )
    }

    @Test
    fun `does not flag non-401 BadResponseException as token problem`() {
        val cause = BadResponseException("server error", 500)

        val exception = AttributionException(cause)

        assertFalse(exception.wasApiTokenInvalid())
        assertEquals("Attribution post request failed", exception.message)
    }

    @Test
    fun `detects invalid api token through nested cause chain`() {
        val nested = BadResponseException("unauthorized", 401)
        val mid = RuntimeException("intermediate", nested)
        val outer = IllegalStateException("outer", mid)

        val exception = AttributionException(outer)

        assertTrue(exception.wasApiTokenInvalid())
    }

    @Test
    fun `returns false when cause chain ends without BadResponseException`() {
        val nested = RuntimeException("nothing related")
        val outer = IllegalStateException("outer", nested)

        val exception = AttributionException(outer)

        assertFalse(exception.wasApiTokenInvalid())
    }
}
