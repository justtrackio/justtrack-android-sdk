package io.justtrack.events

import io.justtrack.AppEvent

/**
 * You can use this event to capture details of items removed from the user's inventory. It could be either weapons or in-app currency.
 */
class JtResourceEvent : AppEvent {
    constructor(jtAction: String, jtItemType: String?, jtItemName: String?, jtItemId: String?, count: Double) : super(NAME) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_ITEM_TYPE, jtItemType)
        addDimension(Dimension.JT_ITEM_NAME, jtItemName)
        addDimension(Dimension.JT_ITEM_ID, jtItemId)
        setValue(count, Unit.COUNT)
    }

    constructor(jtAction: String, jtItemType: String?, jtItemName: String?, jtItemId: String?) : super(NAME) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_ITEM_TYPE, jtItemType)
        addDimension(Dimension.JT_ITEM_NAME, jtItemName)
        addDimension(Dimension.JT_ITEM_ID, jtItemId)
    }

    /** Constants for [JtResourceEvent]. */
    companion object {
        /** The canonical event name sent to the backend. */
        const val NAME: String = "jt_resource"
    }
}
