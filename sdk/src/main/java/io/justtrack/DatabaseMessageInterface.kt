package io.justtrack

internal interface DatabaseMessageInterface : AutoCloseable {
    suspend fun insertMessage(message: LogMessageEntity): Long?

    suspend fun insertMessages(messages: List<LogMessageEntity>): List<Long?>

    suspend fun deleteByIdMessage(idList: List<Long>): Boolean

    suspend fun deleteByDateMessage(cutoffMS: Long): Boolean

    suspend fun markMessage(idList: List<Long>): Boolean

    suspend fun unMarkMessage(idList: List<Long>): Boolean

    suspend fun getNextBatchAndMarkTransactionMessage(batchSize: Int = 100): List<LogMessageEntity>

    suspend fun getAllMessage(): List<LogMessageEntity>

    suspend fun getAllUnMarkMessage(): List<LogMessageEntity>

    suspend fun nukeTableMessage(): Boolean
}
