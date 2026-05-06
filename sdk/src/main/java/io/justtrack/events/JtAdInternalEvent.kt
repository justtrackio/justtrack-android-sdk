package io.justtrack.events

import io.justtrack.AppEvent
import java.util.Date

/**
 * This is an internal event to pass ad impression information.
 */
internal class JtAdInternalEvent
@Suppress("LongParameterList")
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
    revenue: Money?,
    happenedAt: Date,
) : AppEvent(NAME, 0.0, null, null, happenedAt) {
    init {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_AD_BUNDLE_ID, jtAdBundleId)
        addDimension(Dimension.JT_AD_INSTANCE_NAME, jtAdInstanceName)
        addDimension(Dimension.JT_AD_NETWORK, jtAdNetwork)
        addDimension(Dimension.JT_AD_PLACEMENT, jtAdPlacement)
        addDimension(Dimension.JT_AD_SDK, jtAdSdk)
        addDimension(Dimension.JT_AD_SEGMENT, jtAdSegment)
        addDimension(Dimension.JT_AD_UNIT, jtAdUnit)
        addDimension(Dimension.JT_AD_TEST_GROUP, jtAdTestGroup)
        if (revenue != null) {
            setValue(revenue)
        }
    }

    companion object {
        const val NAME: String = "jt_ad"
    }
}
