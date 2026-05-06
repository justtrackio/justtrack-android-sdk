package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MessageRepositoryImpl constructor(
    val formatter: Formatter,
    private val databaseInterface: DatabaseMessageInterface,
    private val logger: Logger,
) : EntityRepository<LogStoreMessage> {
    override fun close() {
        databaseInterface.close()
    }

    override suspend fun storeEntity(data: LogStoreMessage): Long? = withContext(Dispatchers.IO) {
        databaseInterface.insertMessage(LogMessageEntity(data, formatter))
    }

    override suspend fun storeEntities(dataList: List<LogStoreMessage>) {
        databaseInterface.insertMessages(dataList.map { LogMessageEntity(it, formatter) })
    }

    override suspend fun removeEntitiesByDate(cutoffMS: Long) {
        databaseInterface.deleteByDateMessage(cutoffMS)
    }

    override suspend fun fetchNextBatchAndMark(batchSize: Int): List<LogStoreMessage> {
        return databaseInterface.getNextBatchAndMarkTransactionMessage(batchSize).map {
            it.transform(formatter, logger)
        }
    }

    override suspend fun deleteEntities(dataList: List<LogStoreMessage>) {
        if (dataList.isNotEmpty()) {
            databaseInterface.deleteByIdMessage(dataList.map { it.id })
        }
    }

    override suspend fun markEntitiesById(idList: List<Long>) {
        databaseInterface.markMessage(idList = idList)
    }

    override suspend fun unMarkEntitiesById(idList: List<Long>) {
        databaseInterface.unMarkMessage(idList = idList)
    }

    @VisibleForTesting
    override suspend fun getAll(): List<LogStoreMessage> {
        return databaseInterface.getAllMessage().map {
            it.transform(formatter, logger)
        }
    }

    @VisibleForTesting
    override suspend fun getAllUnMark(): List<LogStoreMessage> {
        return databaseInterface.getAllUnMarkMessage().map {
            it.transform(formatter, logger)
        }
    }
}
