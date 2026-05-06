package io.justtrack.events

import io.justtrack.AppEvent

/**
 * You can use this event to capture details of an ad like load, click, show etc.
 * Ad impressions are automatically tracked by justtrack SDK for integrated networks.
 * For networks not integrated you can use forwardAdImpression().
 */
class JtAdEvent : AppEvent {
    @Suppress("LongParameterList") // we need all of these dimensions
    constructor(
        jtAction: String,
        jtAdBundleId: String?,
        jtAdInstanceName: String?,
        jtAdNetwork: String?,
        jtAdPlacement: String?,
        jtAdSdk: String?,
        jtAdSegment: String?,
        jtAdUnit: String?,
        jtAdTestGroup: String?,
        duration: Double,
        unit: TimeUnitGroup,
    ) : super(NAME) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_AD_BUNDLE_ID, jtAdBundleId)
        addDimension(Dimension.JT_AD_INSTANCE_NAME, jtAdInstanceName)
        addDimension(Dimension.JT_AD_NETWORK, jtAdNetwork)
        addDimension(Dimension.JT_AD_PLACEMENT, jtAdPlacement)
        addDimension(Dimension.JT_AD_SDK, jtAdSdk)
        addDimension(Dimension.JT_AD_SEGMENT, jtAdSegment)
        addDimension(Dimension.JT_AD_UNIT, jtAdUnit)
        addDimension(Dimension.JT_AD_TEST_GROUP, jtAdTestGroup)
        setValue(duration, unit.base)
    }

    @Suppress("LongParameterList") // we need all of these dimensions
    constructor(
        jtAction: String,
        jtAdBundleId: String?,
        jtAdInstanceName: String?,
        jtAdNetwork: String?,
        jtAdPlacement: String?,
        jtAdSdk: String?,
        jtAdSegment: String?,
        jtAdUnit: String?,
        jtAdTestGroup: String?,
    ) : super(NAME) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_AD_BUNDLE_ID, jtAdBundleId)
        addDimension(Dimension.JT_AD_INSTANCE_NAME, jtAdInstanceName)
        addDimension(Dimension.JT_AD_NETWORK, jtAdNetwork)
        addDimension(Dimension.JT_AD_PLACEMENT, jtAdPlacement)
        addDimension(Dimension.JT_AD_SDK, jtAdSdk)
        addDimension(Dimension.JT_AD_SEGMENT, jtAdSegment)
        addDimension(Dimension.JT_AD_UNIT, jtAdUnit)
        addDimension(Dimension.JT_AD_TEST_GROUP, jtAdTestGroup)
    }

    /** Constants for [JtAdEvent]. */
    companion object {
        /** The canonical event name sent to the backend. */
        const val NAME: String = "jt_ad"
    }
}
