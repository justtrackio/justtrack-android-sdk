package io.justtrack.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.MalformedURLException

@RunWith(RobolectricTestRunner::class)
internal class UrlTest {

    @Test
    fun stripScheme_returnsAuthorityAndPath() {
        assertEquals("example.com/path", stripScheme("https://example.com/path"))
    }

    @Test
    fun stripScheme_preservesEncodedQuery() {
        assertEquals(
            "example.com/path?a=1&b=two%20words",
            stripScheme("https://example.com/path?a=1&b=two%20words"),
        )
    }

    @Test
    fun stripScheme_preservesEncodedFragment() {
        assertEquals(
            "example.com/path#section%20one",
            stripScheme("https://example.com/path#section%20one"),
        )
    }

    @Test
    fun stripScheme_combinesAuthorityPathQueryAndFragment() {
        assertEquals(
            "example.com/p?q=1#frag",
            stripScheme("https://example.com/p?q=1#frag"),
        )
    }

    @Test
    fun stripScheme_returnsOnlyPathWhenNoAuthority() {
        // file: URLs have no authority – exercises the authority?.let null branch.
        val result = stripScheme("file:/tmp/test")
        assertEquals("/tmp/test", result)
    }

    @Test
    fun stripScheme_returnsOnlyAuthorityWhenPathIsEmpty() {
        // Opaque URI – Uri.encodedPath is null, exercising the encodedPath?.let null branch.
        val result = stripScheme("mailto:test@example.com")
        assertEquals("", result)
    }

    @Test
    fun stripScheme_omitsQueryWhenAbsent() {
        // Exercises the encodedQuery null branch.
        val result = stripScheme("https://example.com/path")
        assertEquals("example.com/path", result)
        // No '?' must appear.
        assert(!result.contains("?"))
    }

    @Test
    fun stripScheme_omitsFragmentWhenAbsent() {
        // Exercises the encodedFragment null branch.
        val result = stripScheme("https://example.com/path?q=1")
        assertEquals("example.com/path?q=1", result)
        assert(!result.contains("#"))
    }

    @Test
    fun stripScheme_throwsForMalformedUrl() {
        assertThrows(MalformedURLException::class.java) {
            stripScheme("not a real url")
        }
    }
}
