package io.justtrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BillingTrackerFactoryTest {
    @Test
    fun `detects supported BillingClient versions`() {
        assertEquals(
            BillingTrackerFactory.BillingVersion.VERSION_5,
            BillingTrackerFactory.getBillingClientVersion("5.2.1"),
        )
        assertEquals(
            BillingTrackerFactory.BillingVersion.VERSION_6,
            BillingTrackerFactory.getBillingClientVersion("6.2.1"),
        )
        assertEquals(
            BillingTrackerFactory.BillingVersion.VERSION_7,
            BillingTrackerFactory.getBillingClientVersion("7.1.1"),
        )
        assertEquals(
            BillingTrackerFactory.BillingVersion.VERSION_8,
            BillingTrackerFactory.getBillingClientVersion("8.2.1"),
        )
    }

    @Test
    fun `uses BillingClient 8 compatibility for newer versions`() {
        assertEquals(
            BillingTrackerFactory.BillingVersion.VERSION_8,
            BillingTrackerFactory.getBillingClientVersion("9.1.0"),
        )
    }

    @Test
    fun `extracts product details from BillingClient 8 result`() {
        val productDetails = listOf(Any(), Any())

        assertEquals(
            productDetails,
            extractBillingEightProductDetails(FakeQueryProductDetailsResult(productDetails)),
        )
    }

    @Test
    fun `rejects invalid BillingClient 8 product detail results`() {
        assertNull(extractBillingEightProductDetails(FakeQueryProductDetailsResult("invalid")))
        assertNull(extractBillingEightProductDetails(FakeQueryProductDetailsResult(listOf(Any(), null))))
    }

    class FakeQueryProductDetailsResult(
        private val productDetails: Any,
    ) {
        @Suppress("unused")
        fun getProductDetailsList(): Any = productDetails
    }
}
