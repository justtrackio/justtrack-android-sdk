package io.justtrack.events

import org.junit.Assert.assertEquals
import org.junit.Test

class DimensionTest {
    @Test
    fun testJtAction() {
        assertEquals("jt_action", Dimension.JT_ACTION.toString())
    }

    @Test
    fun testJtAdBundleId() {
        assertEquals("jt_ad_bundle_id", Dimension.JT_AD_BUNDLE_ID.toString())
    }

    @Test
    fun testJtAdInstanceName() {
        assertEquals("jt_ad_instance_name", Dimension.JT_AD_INSTANCE_NAME.toString())
    }

    @Test
    fun testJtAdNetwork() {
        assertEquals("jt_ad_network", Dimension.JT_AD_NETWORK.toString())
    }

    @Test
    fun testJtAdPlacement() {
        assertEquals("jt_ad_placement", Dimension.JT_AD_PLACEMENT.toString())
    }

    @Test
    fun testJtAdSdk() {
        assertEquals("jt_ad_sdk", Dimension.JT_AD_SDK.toString())
    }

    @Test
    fun testJtAdSegment() {
        assertEquals("jt_ad_segment", Dimension.JT_AD_SEGMENT.toString())
    }

    @Test
    fun testJtAdTestGroup() {
        assertEquals("jt_ad_test_group", Dimension.JT_AD_TEST_GROUP.toString())
    }

    @Test
    fun testJtAdUnit() {
        assertEquals("jt_ad_unit", Dimension.JT_AD_UNIT.toString())
    }

    @Test
    fun testJtCategory() {
        assertEquals("jt_category", Dimension.JT_CATEGORY.toString())
    }

    @Test
    fun testJtContext() {
        assertEquals("jt_context", Dimension.JT_CONTEXT.toString())
    }

    @Test
    fun testJtDetail() {
        assertEquals("jt_detail", Dimension.JT_DETAIL.toString())
    }

    @Test
    fun testJtItemId() {
        assertEquals("jt_item_id", Dimension.JT_ITEM_ID.toString())
    }

    @Test
    fun testJtItemName() {
        assertEquals("jt_item_name", Dimension.JT_ITEM_NAME.toString())
    }

    @Test
    fun testJtItemType() {
        assertEquals("jt_item_type", Dimension.JT_ITEM_TYPE.toString())
    }

    @Test
    fun testJtLocation() {
        assertEquals("jt_location", Dimension.JT_LOCATION.toString())
    }

    @Test
    fun testJtMethod() {
        assertEquals("jt_method", Dimension.JT_METHOD.toString())
    }

    @Test
    fun testJtProductId() {
        assertEquals("jt_product_id", Dimension.JT_PRODUCT_ID.toString())
    }

    @Test
    fun testJtProductType() {
        assertEquals("jt_product_type", Dimension.JT_PRODUCT_TYPE.toString())
    }

    @Test
    fun testJtProgression1() {
        assertEquals("jt_progression_1", Dimension.JT_PROGRESSION_1.toString())
    }

    @Test
    fun testJtProgression2() {
        assertEquals("jt_progression_2", Dimension.JT_PROGRESSION_2.toString())
    }

    @Test
    fun testJtProgression3() {
        assertEquals("jt_progression_3", Dimension.JT_PROGRESSION_3.toString())
    }

    @Test
    fun testJtState() {
        assertEquals("jt_state", Dimension.JT_STATE.toString())
    }

    @Test
    fun testJtToken() {
        assertEquals("jt_token", Dimension.JT_TOKEN.toString())
    }

    @Test
    fun testJtTrigger() {
        assertEquals("jt_trigger", Dimension.JT_TRIGGER.toString())
    }

    @Test
    fun testJtUrl() {
        assertEquals("jt_url", Dimension.JT_URL.toString())
    }
}
