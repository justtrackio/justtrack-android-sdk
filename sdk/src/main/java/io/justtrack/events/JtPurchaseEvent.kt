package io.justtrack.events

import io.justtrack.AppEvent

/**
 * An event that tracks and reports in-app purchase details (for example, the user selected an item
 * from the store, confirmed a purchase, or added an item to the cart). In case of a successful
 * purchase or subscription, justtrack automatically tracks and reports the event, so there is no
 * need to generate it manually.
 */
class JtPurchaseEvent : AppEvent {
    constructor(jtAction: String, jtProductId: String, jtToken: String?, jtProductType: String, count: Double) : super(NAME) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_PRODUCT_ID, jtProductId)
        addDimension(Dimension.JT_TOKEN, jtToken)
        addDimension(Dimension.JT_PRODUCT_TYPE, jtProductType)
        setValue(count, Unit.COUNT)
    }

    constructor(jtAction: String, jtProductId: String, jtToken: String?, jtProductType: String) : super(NAME) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_PRODUCT_ID, jtProductId)
        addDimension(Dimension.JT_TOKEN, jtToken)
        addDimension(Dimension.JT_PRODUCT_TYPE, jtProductType)
    }

    /** Constants for [JtPurchaseEvent]. */
    companion object {
        /** The canonical event name sent to the backend. */
        const val NAME: String = "jt_purchase"
    }
}
