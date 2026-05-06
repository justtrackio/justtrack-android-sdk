package io.justtrack.events

import io.justtrack.AppEvent
import java.util.Date

/**
 * The app was launched by the user and did not run before. The duration denotes the time the app
 * was running before the SDK was initialized. The event is automatically sent by the justtrack SDK.
 */
internal class JtAppOpenEvent : AppEvent {
    constructor(sessionId: String, duration: Double, unit: TimeUnitGroup, happenedAt: Date) : super(NAME, 0.0, null, null, happenedAt) {
        setValue(duration, unit.base)
        super.sessionId = sessionId
    }

    constructor(sessionId: String, happenedAt: Date) : super(NAME, 0.0, null, null, happenedAt) {
        super.sessionId = sessionId
    }

    companion object {
        const val NAME: String = "jt_app_open"
    }
}
