package io.justtrack.events

import io.justtrack.AppEvent

/**
 * To capture all details related to user login events.
 */
class JtLoginEvent(jtAction: String, jtMethod: String?) : AppEvent(NAME) {
    init {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_METHOD, jtMethod)
    }

    /** Constants for [JtLoginEvent]. */
    companion object {
        /** The canonical event name sent to the backend. */
        const val NAME: String = "jt_login"
    }
}
