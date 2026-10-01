package io.justtrack

import java.util.Date
import java.util.UUID

internal data class StorableEvent(
    val id: Long = -1,
    val eventId: UUID,
    val event: PublishableAppEvent,
    val sequenceNumber: Long = -1,
) {
    fun getHappenedAt(): Date = event.happenedAt
}
