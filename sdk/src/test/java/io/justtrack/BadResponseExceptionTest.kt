package io.justtrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BadResponseExceptionTest {

    @Test
    fun `two-arg constructor stores code and leaves body null`() {
        val ex = BadResponseException("msg", 500)

        assertEquals("msg", ex.message)
        assertEquals(500, ex.responseCode)
        assertNull(ex.body)
        assertNull(ex.cause)
    }

    @Test
    fun `three-arg constructor stores body`() {
        val ex = BadResponseException("msg", 503, "service unavailable")

        assertEquals(503, ex.responseCode)
        assertEquals("service unavailable", ex.body)
        assertNull(ex.cause)
    }

    @Test
    fun `four-arg constructor stores body and cause`() {
        val cause = IOException("io")
        val ex = BadResponseException("msg", 502, "bad gateway", cause)

        assertEquals(502, ex.responseCode)
        assertEquals("bad gateway", ex.body)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `formatBadResponseStatusMessage formats generic non-401 with body`() {
        val msg = BadResponseException.formatBadResponseStatusMessage(500, "Server Error", "oops")

        assertEquals("Received invalid response status 500 Server Error with body oops", msg)
    }

    @Test
    fun `formatBadResponseStatusMessage omits body section when body is null`() {
        val msg = BadResponseException.formatBadResponseStatusMessage(404, "Not Found", null)

        assertEquals("Received invalid response status 404 Not Found", msg)
    }

    @Test
    fun `formatBadResponseStatusMessage formats 401 as error box with token hint and short body`() {
        val msg = BadResponseException.formatBadResponseStatusMessage(401, "Unauthorized", "denied")

        assertTrue("missing token hint, got: $msg", msg.contains("Is the API token correct?"))
        assertTrue("missing docs link", msg.contains("https://docs.justtrack.io/sdk/latest/overview/find-your-justtrack-token"))
        assertTrue("body should be embedded verbatim when short", msg.contains("Response Body: denied"))
        // No truncation for short bodies.
        assertTrue("short body must not be truncated", !msg.contains("denied..."))
    }

    @Test
    fun `formatBadResponseStatusMessage truncates long 401 body to 64 chars with ellipsis`() {
        val longBody = "x".repeat(200)

        val msg = BadResponseException.formatBadResponseStatusMessage(401, "Unauthorized", longBody)

        val expectedPrefix = "Response Body: " + "x".repeat(64) + "..."
        assertTrue("expected truncated body line, got: $msg", msg.contains(expectedPrefix))
        // Original full body must NOT appear in full.
        assertTrue("full body should not appear", !msg.contains("x".repeat(200)))
    }

    @Test
    fun `formatBadResponseStatusMessage handles 401 without body`() {
        val msg = BadResponseException.formatBadResponseStatusMessage(401, "Unauthorized", null)

        assertTrue(msg.contains("Is the API token correct?"))
        // "Response Body: " line is present but empty after the colon.
        assertTrue(msg.contains("Response Body: "))
    }

    @Test
    fun `formatErrorBox wraps each message in padded asterisk frame`() {
        val box = BadResponseException.formatErrorBox("hello", "world!")

        // Starts and ends with newline.
        assertTrue(box.startsWith("\n"))
        assertTrue(box.endsWith("\n"))
        val lines = box.split("\n").filter { it.isNotEmpty() }
        // 2 horizontal borders + 2 message lines = 4 lines.
        assertEquals(4, lines.size)
        // Borders are all asterisks.
        val border = lines.first()
        assertTrue("border should be only asterisks: $border", border.all { it == '*' })
        assertEquals(border, lines.last())
        // Longest msg = "world!" (6). lineLength = 6 + 4 = 10.
        assertEquals(10, border.length)
        // Each message line starts with "* " and ends with " *", padded with spaces.
        for (i in 1..2) {
            val line = lines[i]
            assertTrue("line should start with '* ': $line", line.startsWith("* "))
            assertTrue("line should end with ' *': $line", line.endsWith(" *"))
            assertEquals("all message lines should have same width", border.length, line.length)
        }
    }

    @Test
    fun `formatErrorBox handles single message`() {
        val box = BadResponseException.formatErrorBox("abc")

        val lines = box.split("\n").filter { it.isNotEmpty() }
        // 2 borders + 1 message.
        assertEquals(3, lines.size)
        // lineLength = 3 + 4 = 7.
        assertEquals(7, lines.first().length)
        assertEquals("* abc *", lines[1])
    }
}
