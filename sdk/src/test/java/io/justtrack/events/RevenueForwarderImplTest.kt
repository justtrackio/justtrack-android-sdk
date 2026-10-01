package io.justtrack.events

import io.justtrack.AppEvent
import io.justtrack.AsyncFuture
import io.justtrack.EventTracker
import io.justtrack.SessionManager
import io.justtrack.ValueFuture
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdImpressionState
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal class RevenueForwarderImplTest {
    private val eventTracker: EventTracker = mock()
    private val sessionManager: SessionManager = mock()
    private val logger: Logger = mock()
    private val isTracking = AtomicBoolean(true)
    private val forwarder = RevenueForwarderImpl(eventTracker, sessionManager, isTracking, logger)

    @Test
    fun forwardAdImpression_whenNotTracking_returnsErrorAndDoesNotTrack() {
        isTracking.set(false)

        val future = forwarder.forwardAdImpression(validAdImpression())

        assertFutureFailsWith<SdkNotTrackingException>(future)
        verify(eventTracker, never()).track(any(), any())
    }

    @Test
    fun forwardAdImpression_withNegativeRevenue_returnsErrorAndLogsWarning() {
        val future = forwarder.forwardAdImpression(
            validAdImpression().copy(revenue = Money(-1.0, "USD")),
        )

        assertFutureFailsWith<InvalidFieldException>(future)
        verify(logger).warn(eq("Negative revenue for AdFormat"), any<LoggerFields>())
        verify(eventTracker, never()).track(any(), any())
    }

    @Test
    fun forwardAdImpression_withEmptyAdUnit_returnsErrorAndLogsWarning() {
        val future = forwarder.forwardAdImpression(validAdImpression().copy(unit = ""))

        assertFutureFailsWith<InvalidFieldException>(future)
        verify(logger).warn("Empty AdUnit")
        verify(eventTracker, never()).track(any(), any())
    }

    @Test
    fun forwardAdImpression_withValidAdImpression_tracksAdEvent() {
        val trackedFuture = ValueFuture<Void?>(null)
        whenever(eventTracker.track(any(), any())).thenReturn(trackedFuture)

        val result = forwarder.forwardAdImpression(validAdImpression())

        assertSame(trackedFuture, result)
        val eventCaptor = argumentCaptor<AppEvent>()
        verify(eventTracker).track(eventCaptor.capture(), eq(sessionManager))

        val event = eventCaptor.firstValue
        assertEquals("jt_ad", event.name)
        assertEquals("success", event.dimensions["jt_action"])
        assertEquals("unit", event.dimensions["jt_ad_unit"])
        assertEquals("sdk", event.dimensions["jt_ad_sdk"])
        assertEquals("network", event.dimensions["jt_ad_network"])
        assertEquals("completed", event.dimensions["jt_impression_state"])
        assertTrue(event.toString().contains("value = 1.25 USD"))
    }

    @Test
    fun forwardAdImpression_withoutRevenue_tracksAdEventWithZeroUsd() {
        whenever(eventTracker.track(any(), any())).thenReturn(ValueFuture(null))

        forwarder.forwardAdImpression(validAdImpression().copy(revenue = null))

        val eventCaptor = argumentCaptor<AppEvent>()
        verify(eventTracker).track(eventCaptor.capture(), eq(sessionManager))

        assertTrue(eventCaptor.firstValue.toString().contains("value = 0.0 USD"))
    }

    @Test
    fun forwardAdImpression_withInvalidRevenueCurrency_returnsErrorAndLogsWarning() {
        val future = forwarder.forwardAdImpression(
            validAdImpression().copy(revenue = Money(1.0, "usd")),
        )

        assertFutureFailsWith<InvalidFieldException>(future)
        verify(logger).warn(eq("Not publishing invalid ad impression"), any<LoggerFields>())
        verify(eventTracker, never()).track(any(), any())
    }

    @Test
    fun forwardInApp_withNegativeRevenue_returnsFalseAndDoesNotTrack() {
        val result = forwarder.forwardInApp("product-id", "token", Money(-1.0, "USD"))

        assertFalse(result)
        verify(logger).warn(eq("Negative revenue for product purchase"), any<LoggerFields>())
        verify(eventTracker, never()).track(any(), any())
    }

    @Test
    fun forwardInApp_withInvalidMoney_returnsFalseAndDoesNotTrack() {
        val result = forwarder.forwardInApp("product-id", "token", Money(1.0, "usd"))

        assertFalse(result)
        verify(logger).warn(eq("Not publishing invalid inapp purchase"), any<LoggerFields>())
        verify(eventTracker, never()).track(any(), any())
    }

    @Test
    fun forwardInApp_withValidMoney_tracksPurchaseEvent() {
        whenever(eventTracker.track(any(), any())).thenReturn(ValueFuture(null))

        val result = forwarder.forwardInApp("product-id", "token", Money(2.5, "EUR"))

        assertTrue(result)
        val eventCaptor = argumentCaptor<AppEvent>()
        verify(eventTracker).track(eventCaptor.capture(), eq(sessionManager))

        val event = eventCaptor.firstValue
        assertEquals("jt_purchase", event.name)
        assertEquals("success", event.dimensions["jt_action"])
        assertEquals("product-id", event.dimensions["jt_product_id"])
        assertEquals("token", event.dimensions["jt_token"])
        assertEquals("purchase", event.dimensions["jt_product_type"])
        assertTrue(event.toString().contains("value = 2.5 EUR"))
    }

    @Test
    fun forwardSubscription_withValidMoney_tracksSubscriptionEvent() {
        whenever(eventTracker.track(any(), any())).thenReturn(ValueFuture(null))

        val result = forwarder.forwardSubscription("subscription-id", "token", Money(3.5, "USD"))

        assertTrue(result)
        val eventCaptor = argumentCaptor<AppEvent>()
        verify(eventTracker).track(eventCaptor.capture(), eq(sessionManager))
        assertEquals("subscription", eventCaptor.firstValue.dimensions["jt_product_type"])
        assertEquals("subscription-id", eventCaptor.firstValue.dimensions["jt_product_id"])
    }

    @Test
    fun forwardSubscription_withNegativeRevenue_returnsFalseAndDoesNotTrack() {
        val result = forwarder.forwardSubscription("subscription-id", "token", Money(-1.0, "USD"))

        assertFalse(result)
        verify(logger).warn(eq("Negative revenue for subscription purchase"), any<LoggerFields>())
        verify(eventTracker, never()).track(any(), any())
    }

    private fun validAdImpression(): AdImpression = AdImpression(
        unit = "unit",
        sdkName = "sdk",
        network = "network",
        placement = "placement",
        testGroup = "test-group",
        segmentName = "segment",
        instanceName = "instance",
        bundleId = "bundle",
        revenue = Money(1.25, "USD"),
        state = AdImpressionState.COMPLETED,
    )

    private inline fun <reified T : Throwable> assertFutureFailsWith(future: AsyncFuture<Void?>) {
        val exception = assertThrows(ExecutionException::class.java) { future.get() }
        assertTrue(exception.cause is T)
    }
}
