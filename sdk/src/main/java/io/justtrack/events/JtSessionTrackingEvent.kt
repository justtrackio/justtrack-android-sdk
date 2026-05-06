package io.justtrack.events

import io.justtrack.AppEvent
import java.util.Date

/**
 * The user's session either starts or ends. The duration denotes the time the user stayed in
 * the app. The event is automatically sent by the justtrack SDK.
 */
internal class JtSessionTrackingEvent : AppEvent {
    constructor(sessionId: String, jtAction: String, duration: Double, unit: TimeUnitGroup, happenedAt: Date) : super(
        NAME,
        0.0,
        null,
        null,
        happenedAt,
    ) {
        addDimension(Dimension.JT_ACTION, jtAction)
        setValue(duration, unit.base)
        super.sessionId = sessionId
    }

    constructor(sessionId: String, jtAction: String, happenedAt: Date) : super(NAME, 0.0, null, null, happenedAt) {
        addDimension(Dimension.JT_ACTION, jtAction)
        super.sessionId = sessionId
    }

    companion object {
        const val NAME: String = "jt_session_tracking"
    }
}
