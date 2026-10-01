@file:Suppress("UndocumentedPublicProperty")

package io.justtrack.events

/**
 * This class represents the different dimensions the SDK can send to the backend.
 */
enum class Dimension(private val dimension: String) {
    JT_ACTION("jt_action"),
    JT_AD_BUNDLE_ID("jt_ad_bundle_id"),
    JT_AD_INSTANCE_NAME("jt_ad_instance_name"),
    JT_AD_NETWORK("jt_ad_network"),
    JT_AD_PLACEMENT("jt_ad_placement"),
    JT_AD_SDK("jt_ad_sdk"),
    JT_AD_SEGMENT("jt_ad_segment"),
    JT_AD_TEST_GROUP("jt_ad_test_group"),
    JT_AD_UNIT("jt_ad_unit"),
    JT_CATEGORY("jt_category"),
    JT_CONNECTION_TYPE("jt_connection_type"),
    JT_CONTEXT("jt_context"),
    JT_DETAIL("jt_detail"),
    JT_GLOBAL_0("jt_global_0"),
    JT_GLOBAL_1("jt_global_1"),
    JT_GLOBAL_2("jt_global_2"),
    JT_ITEM_ID("jt_item_id"),
    JT_ITEM_NAME("jt_item_name"),
    JT_ITEM_TYPE("jt_item_type"),
    JT_LOCATION("jt_location"),
    JT_METHOD("jt_method"),
    JT_PRODUCT_ID("jt_product_id"),
    JT_PRODUCT_TYPE("jt_product_type"),
    JT_PROGRESSION_1("jt_progression_1"),
    JT_PROGRESSION_2("jt_progression_2"),
    JT_PROGRESSION_3("jt_progression_3"),
    JT_STATE("jt_state"),
    JT_TOKEN("jt_token"),
    JT_TRIGGER("jt_trigger"),
    JT_URL("jt_url"),
    ;

    override fun toString(): String {
        return dimension
    }
}
