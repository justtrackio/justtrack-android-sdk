package io.justtrack.events

import io.justtrack.AppEvent
import java.util.Date

internal class JtDeeplinkHandledEvent(sessionId: String, jtUrl: String, happenedAt: Date) :
    AppEvent(NAME, 0.0, null, null, happenedAt) {
    init {
        addDimension(Dimension.JT_URL, jtUrl)
        super.sessionId = sessionId
    }

    companion object {
        const val NAME: String = "jt_deeplink_handled"
    }
}
