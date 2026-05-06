package io.justtrack.events

import io.justtrack.AppEvent
import java.util.Date

/**
 * The app was installed by the user and launched for the first time. Event is automatically send by the justtrack SDK.
 */
internal class JtAppInstallEvent : AppEvent {
    internal constructor(sessionId: String, duration: Double, unit: TimeUnitGroup, happenedAt: Date) : super(NAME, 0.0, null, null, happenedAt) {
        setValue(duration, unit.base)
        super.sessionId = sessionId
    }

    internal constructor(sessionId: String, happenedAt: Date) : super(NAME, 0.0, null, null, happenedAt) {
        super.sessionId = sessionId
    }

    companion object {
        const val NAME: String = "jt_app_install"
    }
}
