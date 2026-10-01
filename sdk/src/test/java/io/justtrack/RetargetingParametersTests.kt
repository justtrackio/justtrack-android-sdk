package io.justtrack

import android.content.Intent
import android.net.Uri
import io.justtrack.attribution.Attribution
import io.justtrack.retargeting.PreliminaryRetargetingParameters.ValidateResult
import io.justtrack.retargeting.RetargetingParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RetargetingParametersImplTest {
    @Test
    fun `exposes uri parameters and wasAlreadyInstalled true`() {
        val params = mapOf("a" to "1", "b" to "2")
        val r = RetargetingParametersImpl(true, "https://example.com/x", params)
        assertEquals(Uri.parse("https://example.com/x"), r.uri)
        assertEquals(params, r.parameters)
        assertTrue(r.wasAlreadyInstalled())
    }

    @Test
    fun `wasAlreadyInstalled false`() {
        val r = RetargetingParametersImpl(false, "https://example.com", emptyMap())
        assertFalse(r.wasAlreadyInstalled())
    }

    @Test
    fun `parameters map is unmodifiable`() {
        val params = mutableMapOf("k" to "v")
        val r = RetargetingParametersImpl(false, "https://example.com", params)
        var threw: Throwable? = null
        try {
            (r.parameters as MutableMap<String, String>)["new"] = "x"
        } catch (e: UnsupportedOperationException) {
            threw = e
        }
        assertNotNull(threw)
    }

    @Test
    fun `promotionParameter returns null when not present`() {
        val r = RetargetingParametersImpl(false, "https://example.com", emptyMap())
        assertNull(r.promotionParameter)
    }

    @Test
    fun `promotionParameter returns null when value is empty`() {
        val r = RetargetingParametersImpl(false, "https://example.com", mapOf("promo_code" to ""))
        assertNull(r.promotionParameter)
    }

    @Test
    fun `promotionParameter returns code when set`() {
        val r = RetargetingParametersImpl(false, "https://example.com", mapOf("promo_code" to "CODE42"))
        assertEquals("CODE42", r.promotionParameter)
    }
}

@RunWith(RobolectricTestRunner::class)
class PreliminaryRetargetingParametersImplTest {
    @Test
    fun `fromIntent returns null when intent is null`() {
        assertNull(PreliminaryRetargetingParametersImpl.fromIntent(null))
    }

    @Test
    fun `fromIntent returns null when action is not VIEW`() {
        val intent = Intent(Intent.ACTION_MAIN, Uri.parse("https://example.com"))
        intent.putExtra("k", "v")
        assertNull(PreliminaryRetargetingParametersImpl.fromIntent(intent))
    }

    @Test
    fun `fromIntent returns null when dataString is null`() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("k", "v")
        assertNull(PreliminaryRetargetingParametersImpl.fromIntent(intent))
    }

    @Test
    fun `fromIntent returns null when extras is null`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        // no extras -> intent.getExtras() returns null
        assertNull(PreliminaryRetargetingParametersImpl.fromIntent(intent))
    }

    @Test
    fun `fromIntent extracts params and skips null values`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/path"))
        intent.putExtra("foo", "bar")
        intent.putExtra("nullable", null as String?)
        intent.putExtra("count", 42) // non-string extra -> getString returns null
        val r = PreliminaryRetargetingParametersImpl.fromIntent(intent)
        assertNotNull(r)
        assertEquals("bar", r!!.parameters["foo"])
        assertFalse(r.parameters.containsKey("nullable"))
        assertFalse(r.parameters.containsKey("count"))
        assertTrue(r.wasAlreadyInstalled())
        assertEquals(Uri.parse("https://example.com/path"), r.uri)
    }

    @Test
    fun `validate returns same future and resolve completes it`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        intent.putExtra("a", "b")
        val r = PreliminaryRetargetingParametersImpl.fromIntent(intent)!!
        val f1 = r.validate()
        val f2 = r.validate()
        assertSame(f1, f2)

        val attribution = Mockito.mock(Attribution::class.java)
        val validParams = Mockito.mock(RetargetingParameters::class.java)
        val result = object : ValidateResult {
            override val isValid: Boolean = true
            override fun validParameters(): RetargetingParameters? = validParams
            override val attribution: Attribution = attribution
        }
        r.resolve(result)
        @Suppress("UNCHECKED_CAST")
        val obtained = (f1 as ResolvableFuture<ValidateResult?>)
        var captured: ValidateResult? = null
        obtained.registerCallback(object : Callback<ValidateResult?> {
            override fun resolve(response: ValidateResult?) {
                captured = response
            }
            override fun reject(exception: Throwable) { /* no-op */ }
        })
        assertSame(result, captured)
    }

    @Test
    fun `reject propagates exception`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        intent.putExtra("a", "b")
        val r = PreliminaryRetargetingParametersImpl.fromIntent(intent)!!
        val ex = RuntimeException("boom")
        r.reject(ex)
        @Suppress("UNCHECKED_CAST")
        val f = r.validate() as ResolvableFuture<ValidateResult?>
        var captured: Throwable? = null
        f.registerCallback(object : Callback<ValidateResult?> {
            override fun resolve(response: ValidateResult?) { /* no-op */ }
            override fun reject(exception: Throwable) {
                captured = exception
            }
        })
        assertSame(ex, captured?.cause)
    }
}
