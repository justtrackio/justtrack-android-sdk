package io.justtrack.events

import io.justtrack.ProductDetail
import io.justtrack.ProductPurchase
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdImpressionState
import io.justtrack.ads.AdUnit
import io.justtrack.exceptions.InvalidFieldException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EventModelCoverageTest {
    @Test
    fun `product models expose data class behavior`() {
        val money = Money(1.5, "EUR")
        val detail = ProductDetail("product", money)
        val sameDetail = detail.copy()
        val differentDetail = detail.copy(productId = "other")

        assertEquals("product", detail.productId)
        assertSame(money, detail.money)
        assertEquals(detail, sameDetail)
        assertEquals(detail.hashCode(), sameDetail.hashCode())
        assertNotEquals(detail, differentDetail)
        assertTrue(detail.toString().contains("product"))

        val purchase = ProductPurchase(listOf("one", "two"), "token", 2, "order", "json")
        val samePurchase = purchase.copy()
        val differentPurchase = purchase.copy(quantity = 3)

        assertEquals(listOf("one", "two"), purchase.productIds)
        assertEquals("token", purchase.purchaseToken)
        assertEquals(2, purchase.quantity)
        assertEquals("order", purchase.orderId)
        assertEquals("json", purchase.purchaseJson)
        assertEquals(purchase, samePurchase)
        assertEquals(purchase.hashCode(), samePurchase.hashCode())
        assertNotEquals(purchase, differentPurchase)
        assertTrue(purchase.toString().contains("token"))
    }

    @Test
    fun `ad units and states expose encoded names`() {
        assertEquals("banner", AdUnit.Banner.encodedName)
        assertEquals("interstitial", AdUnit.Interstitial.encodedName)
        assertEquals("rewarded", AdUnit.Rewarded.encodedName)
        assertEquals("rewarded_interstitial", AdUnit.RewardedInterstitial.encodedName)
        assertEquals("native", AdUnit.Native.encodedName)
        assertEquals("app_open", AdUnit.AppOpen.encodedName)
        assertEquals("leader", AdUnit.Leader.encodedName)
        assertEquals("mrec", AdUnit.MediumRectangle.encodedName)
        assertEquals("skipped", AdImpressionState.SKIPPED.encodedName)
        assertEquals("completed", AdImpressionState.COMPLETED.encodedName)
    }

    @Test
    fun `ad impression secondary constructor maps unit`() {
        val money = Money(0.5, "USD")
        val impression = AdImpression(
            AdUnit.RewardedInterstitial,
            "sdk",
            network = "network",
            placement = "placement",
            testGroup = "test",
            segmentName = "segment",
            instanceName = "instance",
            bundleId = "bundle",
            revenue = money,
            state = AdImpressionState.COMPLETED,
        )

        assertEquals("rewarded_interstitial", impression.unit)
        assertEquals("sdk", impression.sdkName)
        assertEquals("network", impression.network)
        assertEquals("placement", impression.placement)
        assertEquals("test", impression.testGroup)
        assertEquals("segment", impression.segmentName)
        assertEquals("instance", impression.instanceName)
        assertEquals("bundle", impression.bundleId)
        assertSame(money, impression.revenue)
        assertEquals(AdImpressionState.COMPLETED, impression.state)
    }

    @Test
    fun `ad impression supports data class behavior`() {
        val impression = AdImpression(
            AdUnit.RewardedInterstitial,
            "sdk",
            network = "network",
            placement = "placement",
            testGroup = "test",
            segmentName = "segment",
            instanceName = "instance",
            bundleId = "bundle",
            revenue = Money(0.5, "USD"),
            state = AdImpressionState.COMPLETED,
        )

        val same = impression.copy()
        assertEquals(impression, same)
        assertEquals(impression.hashCode(), same.hashCode())
        assertNotEquals(impression, impression.copy(unit = "banner"))
        assertNotEquals(impression, impression.copy(sdkName = "other"))
        assertNotEquals(impression, impression.copy(network = "other"))
        assertNotEquals(impression, impression.copy(placement = "other"))
        assertNotEquals(impression, impression.copy(testGroup = "other"))
        assertNotEquals(impression, impression.copy(segmentName = "other"))
        assertNotEquals(impression, impression.copy(instanceName = "other"))
        assertNotEquals(impression, impression.copy(bundleId = "other"))
        assertNotEquals(impression, impression.copy(revenue = Money(1.0, "USD")))
        assertNotEquals(impression, impression.copy(state = AdImpressionState.SKIPPED))
        assertTrue(impression.toString().contains("rewarded_interstitial"))
    }

    @Test
    fun `ad impression defaults and mutable fields work`() {
        val money = Money(0.5, "USD")
        val defaults = AdImpression("banner", "sdk")
        assertNull(defaults.network)
        assertNull(defaults.placement)
        assertNull(defaults.testGroup)
        assertNull(defaults.segmentName)
        assertNull(defaults.instanceName)
        assertNull(defaults.bundleId)
        assertNull(defaults.revenue)
        assertNull(defaults.state)

        defaults.network = "network"
        defaults.placement = "placement"
        defaults.testGroup = "test"
        defaults.segmentName = "segment"
        defaults.instanceName = "instance"
        defaults.bundleId = "bundle"
        defaults.revenue = money
        assertEquals("network", defaults.network)
        assertEquals("placement", defaults.placement)
        assertEquals("test", defaults.testGroup)
        assertEquals("segment", defaults.segmentName)
        assertEquals("instance", defaults.instanceName)
        assertEquals("bundle", defaults.bundleId)
        assertSame(money, defaults.revenue)

        val enumDefaults = AdImpression(AdUnit.Banner, "sdk")
        assertEquals("banner", enumDefaults.unit)
        assertEquals("sdk", enumDefaults.sdkName)
        assertNull(enumDefaults.network)
        assertNull(enumDefaults.state)
    }

    @Test
    fun `money validates finite uppercase iso currency and stringifies`() {
        val money = Money(2.5, "USD")

        money.validate()
        assertEquals(2.5, money.value, 0.0)
        assertEquals("USD", money.currency)
        assertEquals("Money{value=2.5, currency='USD'}", money.toString())

        assertInvalidMoney { Money(Double.NaN, "USD").validate() }
        assertInvalidMoney { Money(Double.POSITIVE_INFINITY, "USD").validate() }
        assertInvalidMoney { Money(1.0, "US").validate() }
        assertInvalidMoney { Money(1.0, "usd").validate() }
    }

    @Test
    fun `specialized event constructors set names dimensions and values`() {
        val progression = JtProgressionEvent(JtProgressionEvent.Action.COMPLETE, "world", "level", "quest", 3.0, TimeUnitGroup.SECONDS)
        val progressionWithoutDuration = JtProgressionEvent(JtProgressionEvent.Action.START, "world", "level", "quest")
        val resource = JtResourceEvent("spend", "currency", "gold", "gold-1", 7.0)
        val ad = JtAdEvent(
            "show",
            "bundle",
            "instance",
            "network",
            "placement",
            "sdk",
            "segment",
            "rewarded",
            "test",
            4.0,
            TimeUnitGroup.MILLISECONDS,
        )

        assertTrue(progression.toString().contains("jt_progression"))
        assertTrue(progression.toString().contains("jt_action = complete"))
        assertTrue(progression.toString().contains("jt_progression_1 = world"))
        assertTrue(progression.toString().contains("value = 3000.0 milliseconds"))
        assertTrue(progressionWithoutDuration.toString().contains("jt_action = start"))
        assertTrue(progressionWithoutDuration.toString().contains("jt_progression_2 = level"))

        assertTrue(resource.toString().contains("jt_resource"))
        assertTrue(resource.toString().contains("jt_item_type = currency"))
        assertTrue(resource.toString().contains("value = 7.0 count"))

        assertTrue(ad.toString().contains("jt_ad"))
        assertTrue(ad.toString().contains("jt_ad_bundle_id = bundle"))
        assertTrue(ad.toString().contains("jt_ad_test_group = test"))
        assertTrue(ad.toString().contains("value = 4.0 milliseconds"))
    }

    @Test
    fun `specialized event constructors support optional values`() {
        val progression = JtProgressionEvent("start", "world", null, null)
        val resource = JtResourceEvent("earn", null, null, null)
        val ad = JtAdEvent("click", null, null, null, null, null, null, null, null)

        assertTrue(progression.toString().contains("jt_action = start"))
        assertFalse(progression.toString().contains("jt_progression_2"))
        assertTrue(resource.toString().contains("jt_action = earn"))
        assertFalse(resource.toString().contains("jt_item_type"))
        assertTrue(ad.toString().contains("jt_action = click"))
        assertFalse(ad.toString().contains("jt_ad_bundle_id"))
    }

    @Test
    fun `app install and open events support duration and plain constructors`() {
        val happenedAt = java.util.Date(1L)
        val installWithDuration = JtAppInstallEvent("install-session", 2.0, TimeUnitGroup.SECONDS, happenedAt)
        val installPlain = JtAppInstallEvent("install-session", happenedAt)
        val openWithDuration = JtAppOpenEvent("open-session", 3.0, TimeUnitGroup.MILLISECONDS, happenedAt)
        val openPlain = JtAppOpenEvent("open-session", happenedAt)

        assertTrue(installWithDuration.toString().contains("jt_app_install"))
        assertTrue(installWithDuration.toString().contains("sessionId = install-session"))
        assertTrue(installWithDuration.toString().contains("value = 2000.0 milliseconds"))
        assertTrue(installPlain.toString().contains("value = 0.0 null"))
        assertTrue(openWithDuration.toString().contains("jt_app_open"))
        assertTrue(openWithDuration.toString().contains("sessionId = open-session"))
        assertTrue(openWithDuration.toString().contains("value = 3.0 milliseconds"))
        assertTrue(openPlain.toString().contains("value = 0.0 null"))
    }

    @Test
    fun `dimensions and time units stringify to encoded values`() {
        assertEquals("jt_action", Dimension.JT_ACTION.toString())
        assertEquals("jt_ad_unit", Dimension.JT_AD_UNIT.toString())
        assertEquals("milliseconds", TimeUnitGroup.MILLISECONDS.toString())
        assertEquals("seconds", TimeUnitGroup.SECONDS.toString())
    }

    private fun assertInvalidMoney(block: () -> kotlin.Unit) {
        try {
            block()
            throw AssertionError("expected InvalidFieldException")
        } catch (_: InvalidFieldException) {
        }
    }
}
