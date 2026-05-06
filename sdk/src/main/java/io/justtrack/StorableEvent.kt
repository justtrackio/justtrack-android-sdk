package io.justtrack

import java.util.Date
import java.util.UUID

internal open class StorableEvent constructor(
    val id: Long = 0,
    val eventId: UUID,
    val event: PublishableAppEvent,
    val sequenceNumber: Long,
) {
    internal fun getHappenedAt(): Date {
        return event.happenedAt
    }

    override fun toString(): String {
        return "StorableEvent(id=$id, eventId=$eventId, event=$event, happenedAt=${getHappenedAt()}, sequenceNumber=$sequenceNumber)"
    }
}
