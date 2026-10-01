package io.justtrack

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AttributionErrorClassifierTest {

    private val classifier = AttributionErrorClassifier.getInstance()

    @Test
    fun getInstance_returnsSameSingleton() {
        assertSame(AttributionErrorClassifier.getInstance(), AttributionErrorClassifier.getInstance())
    }

    // --- unrecoverable ---

    @Test
    fun unrecoverable_returnsTrueForBadResponse4xx() {
        assertTrue(classifier.unrecoverable(BadResponseException("bad", 400)))
        assertTrue(classifier.unrecoverable(BadResponseException("bad", 404)))
        assertTrue(classifier.unrecoverable(BadResponseException("bad", 499)))
    }

    @Test
    fun unrecoverable_returnsFalseForBadResponse5xx() {
        assertFalse(classifier.unrecoverable(BadResponseException("bad", 500)))
        assertFalse(classifier.unrecoverable(BadResponseException("bad", 503)))
    }

    @Test
    fun unrecoverable_returnsFalseForBadResponseBelow400() {
        // Unknown status (<400) — just retry.
        assertFalse(classifier.unrecoverable(BadResponseException("ok-ish", 300)))
        assertFalse(classifier.unrecoverable(BadResponseException("informational", 199)))
    }

    @Test
    fun unrecoverable_returnsFalseForNetworkProblem() {
        assertFalse(classifier.unrecoverable(NetworkProblemException(IOException("disconnected"))))
    }

    @Test
    fun unrecoverable_returnsTrueForJsonException() {
        assertTrue(classifier.unrecoverable(JSONException("bad json")))
    }

    @Test
    fun unrecoverable_recursesIntoCauseChain() {
        val cause = BadResponseException("bad", 404)
        val wrapper = RuntimeException("outer", cause)

        assertTrue(classifier.unrecoverable(wrapper))
    }

    @Test
    fun unrecoverable_returnsFalseWhenNoCauseAndUnknownType() {
        assertFalse(classifier.unrecoverable(RuntimeException("unknown")))
    }

    // --- waitTime ---

    @Test
    fun waitTime_returnsRandomFiveMinuteWindowFor5xx() {
        repeat(10) {
            val wait = classifier.waitTime(BadResponseException("server", 500))

            // 0..5 minutes in milliseconds, never negative, never >= 5min.
            assertTrue("wait was $wait", wait >= 0.0)
            assertTrue("wait was $wait", wait <= 5.0 * 60.0 * 1000.0)
        }
    }

    @Test
    fun waitTime_returnsZeroForBadResponseUnder500() {
        assertEquals(0.0, classifier.waitTime(BadResponseException("bad", 400)), 0.0)
        assertEquals(0.0, classifier.waitTime(BadResponseException("bad", 200)), 0.0)
    }

    @Test
    fun waitTime_recursesIntoCauseChain() {
        val cause = BadResponseException("server", 500)
        val wrapper = RuntimeException("outer", cause)

        val wait = classifier.waitTime(wrapper)

        assertTrue("wait was $wait", wait >= 0.0)
        assertTrue("wait was $wait", wait <= 5.0 * 60.0 * 1000.0)
    }

    @Test
    fun waitTime_returnsZeroWhenNoCauseAndUnknownType() {
        assertEquals(0.0, classifier.waitTime(RuntimeException("unknown")), 0.0)
    }
}
