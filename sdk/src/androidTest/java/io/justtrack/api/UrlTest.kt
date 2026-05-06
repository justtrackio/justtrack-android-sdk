package io.justtrack.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.MalformedURLException

class UrlTest {

    @Test
    fun testStripSchemeMalformedUrlThrowsException() {
        assertThrows(MalformedURLException::class.java) {
            stripScheme("malformed url")
        }
    }

    @Test
    fun testStripSchemeProperlyFormattedUrlPasses() {
        assertEquals("proper.url", stripScheme("https://proper.url"))
    }
}
