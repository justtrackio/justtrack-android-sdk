package io.justtrack.events

import io.justtrack.AppEvent
import java.util.Date

/**
 * The event is automatically sent by the justtrack SDK upon requesting permission for App Transparency Tracking.
 */
internal class JtTrackingPermissionEvent(jtAction: String, happenedAt: Date) :
    AppEvent(NAME, 0.0, null, null, happenedAt) {
    init {
        addDimension(Dimension.JT_ACTION, jtAction)
    }

    companion object {
        const val NAME: String = "jt_tracking_permission"
    }
}
