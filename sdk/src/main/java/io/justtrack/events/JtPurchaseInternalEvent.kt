package io.justtrack.events

import io.justtrack.AppEvent
import java.util.Date

/**
 * This is an internal event.
 */
internal class JtPurchaseInternalEvent(
    jtAction: String,
    jtProductId: String,
    jtToken: String,
    jtProductType: String,
    revenue: Money?,
    happenedAt: Date,
) :
    AppEvent(NAME, 0.0, null, null, happenedAt) {
    init {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_PRODUCT_ID, jtProductId)
        addDimension(Dimension.JT_TOKEN, jtToken)
        addDimension(Dimension.JT_PRODUCT_TYPE, jtProductType)
        if (revenue != null) {
            setValue(revenue)
        }
    }

    companion object {
        const val NAME: String = "jt_purchase"
    }
}
