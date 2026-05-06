package io.justtrack

import java.util.UUID

internal class PublishingEvent(
    id: Long = -1,
    eventId: UUID,
    event: PublishableAppEvent,
    sequenceNumber: Long = -1,
) : StorableEvent(id, eventId, event, sequenceNumber) {
    override fun equals(other: Any?): Boolean {
        if (other !is PublishingEvent) {
            return false
        }
        return this.id == other.id &&
            this.eventId == other.eventId &&
            this.event == other.event &&
            this.sequenceNumber == other.sequenceNumber
    }

    override fun hashCode(): Int {
        var hashCode: Int = id.hashCode()
        hashCode = 31 * hashCode + eventId.hashCode()
        hashCode = 31 * hashCode + event.hashCode()
        hashCode = 31 * hashCode + sequenceNumber.hashCode()
        return hashCode
    }
}
