package io.justtrack

internal interface DatabaseEventInterface : AutoCloseable {
    suspend fun insertEvent(event: UserEventEntity): Pair<Long, Long>?

    suspend fun insertEvents(events: List<UserEventEntity>): List<Pair<Long, Long>?>

    suspend fun deleteByIdEvent(idList: List<Long>): Boolean

    suspend fun deleteByDateEvent(cutoffMS: Long): Boolean

    suspend fun markEvent(idList: List<Long>): Boolean

    suspend fun unMarkEvent(idList: List<Long>): Boolean

    suspend fun getNextBatchAndMarkTransactionEvent(batchSize: Int = 100): List<UserEventEntity>

    suspend fun getAllEvent(): List<UserEventEntity>

    suspend fun getAllUnMarkEvent(): List<UserEventEntity>

    suspend fun nukeTableEvent(): Boolean
}
