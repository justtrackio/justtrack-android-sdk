package io.justtrack

import android.system.ErrnoException
import android.system.OsConstants
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ConnectException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class FetchClaimErrorClassifierTest {

    private val classifier = FetchClaimErrorClassifier.instance

    @Test
    fun instance_isSingleton() {
        assertNotNull(FetchClaimErrorClassifier.instance)
        assertEquals(FetchClaimErrorClassifier.instance, FetchClaimErrorClassifier.instance)
    }

    // ---------- unrecoverable() ----------

    @Test
    fun unrecoverable_unknownHostException_returnsTrue() {
        assertTrue(classifier.unrecoverable(UnknownHostException("no host")))
    }

    @Test
    fun unrecoverable_connectException_returnsTrue() {
        assertTrue(classifier.unrecoverable(ConnectException("refused")))
    }

    @Test
    fun unrecoverable_errnoENETUNREACH_returnsTrue() {
        val ex = ErrnoException("connect", OsConstants.ENETUNREACH)
        assertTrue(classifier.unrecoverable(ex))
    }

    @Test
    fun unrecoverable_errnoOtherCode_fallsThroughToTrackingClassifier() {
        // Under Robolectric, ErrnoException.errno may not reliably round-trip from the constructor.
        // Verify behavior only if we can construct an ErrnoException whose .errno != ENETUNREACH.
        val other = OsConstants.ENETUNREACH + 1
        val ex = ErrnoException("connect", other)
        assumeTrue("Robolectric did not preserve a distinct errno", ex.errno != OsConstants.ENETUNREACH)
        assertFalse(classifier.unrecoverable(ex))
    }

    @Test
    fun constructorCreatesClassifier() {
        val created = FetchClaimErrorClassifier()

        assertFalse(created.unrecoverable(RuntimeException("unknown")))
    }

    @Test
    fun unrecoverable_badResponseException_delegatesToTrackingClassifier_true() {
        val ex = BadResponseException("nope", 500)
        // BadResponseException -> TrackingEventErrorClassifier returns true
        assertTrue(classifier.unrecoverable(ex))
    }

    @Test
    fun unrecoverable_networkProblemException_delegatesToTrackingClassifier_false() {
        val ex = NetworkProblemException(RuntimeException("io"))
        assertFalse(classifier.unrecoverable(ex))
    }

    @Test
    fun unrecoverable_jsonException_delegatesToTrackingClassifier_true() {
        assertTrue(classifier.unrecoverable(JSONException("bad json")))
    }

    @Test
    fun unrecoverable_unknownExceptionNoCause_returnsFalse() {
        assertFalse(classifier.unrecoverable(RuntimeException("unknown")))
    }

    @Test
    fun unrecoverable_followsCauseChain_unknownHostInCause() {
        val wrapped = RuntimeException("outer", UnknownHostException("inner"))
        assertTrue(classifier.unrecoverable(wrapped))
    }

    @Test
    fun unrecoverable_followsCauseChain_connectExceptionDeeplyNested() {
        val deep = RuntimeException("outer", RuntimeException("middle", ConnectException("inner")))
        assertTrue(classifier.unrecoverable(deep))
    }

    // ---------- isUnreachableException() ----------

    @Test
    fun isUnreachableException_null_returnsFalse() {
        assertFalse(FetchClaimErrorClassifier.isUnreachableException(null))
    }

    @Test
    fun isUnreachableException_unknownHost_returnsTrue() {
        assertTrue(FetchClaimErrorClassifier.isUnreachableException(UnknownHostException("h")))
    }

    @Test
    fun isUnreachableException_connectException_returnsTrue() {
        assertTrue(FetchClaimErrorClassifier.isUnreachableException(ConnectException("c")))
    }

    @Test
    fun isUnreachableException_errnoNetUnreach_returnsTrue() {
        val ex = ErrnoException("op", OsConstants.ENETUNREACH)
        assertTrue(FetchClaimErrorClassifier.isUnreachableException(ex))
    }

    @Test
    fun isUnreachableException_errnoOther_returnsFalse() {
        val other = OsConstants.ENETUNREACH + 1
        val ex = ErrnoException("op", other)
        assumeTrue("Robolectric did not preserve a distinct errno", ex.errno != OsConstants.ENETUNREACH)
        assertFalse(FetchClaimErrorClassifier.isUnreachableException(ex))
    }

    @Test
    fun isUnreachableException_unrelated_returnsFalse() {
        assertFalse(FetchClaimErrorClassifier.isUnreachableException(IllegalStateException("x")))
    }

    @Test
    fun isUnreachableException_walksCauseChain() {
        val nested = RuntimeException("outer", RuntimeException("middle", ConnectException("c")))
        assertTrue(FetchClaimErrorClassifier.isUnreachableException(nested))
    }

    @Test
    fun isUnreachableException_walksCauseChainAndReturnsFalseAtEnd() {
        val nested = RuntimeException("outer", IllegalArgumentException("middle"))
        assertFalse(FetchClaimErrorClassifier.isUnreachableException(nested))
    }

    // ---------- isCriticalException() ----------

    @Test
    fun isCriticalException_null_returnsFalse() {
        assertFalse(FetchClaimErrorClassifier.isCriticalException(null))
    }

    @Test
    fun isCriticalException_badResponse401_returnsTrue() {
        assertTrue(FetchClaimErrorClassifier.isCriticalException(BadResponseException("unauthorized", 401)))
    }

    @Test
    fun isCriticalException_badResponseOther_returnsFalse() {
        assertFalse(FetchClaimErrorClassifier.isCriticalException(BadResponseException("err", 500)))
        assertFalse(FetchClaimErrorClassifier.isCriticalException(BadResponseException("err", 403)))
    }

    @Test
    fun isCriticalException_nonBadResponse_returnsFalse() {
        assertFalse(FetchClaimErrorClassifier.isCriticalException(RuntimeException("x")))
        assertFalse(FetchClaimErrorClassifier.isCriticalException(UnknownHostException("u")))
    }
}
