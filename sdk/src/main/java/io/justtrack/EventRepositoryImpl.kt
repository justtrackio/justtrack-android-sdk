package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.log.Logger

internal open class EventRepositoryImpl(
    private val formatter: Formatter,
    private val platformType: PlatformType,
    private val databaseInterface: DatabaseEventInterface,
    private val logger: Logger,
) : EventRepository {
    override fun close() {
        databaseInterface.close()
    }

    override suspend fun storeEntity(data: PublishingEvent): Pair<Long, Long>? {
        return databaseInterface.insertEvent(
            UserEventEntity(
                data,
                formatter,
            ),
        )
    }

    override suspend fun storeEntities(dataList: List<PublishingEvent>) {
        databaseInterface.insertEvents(
            dataList.map {
                UserEventEntity(
                    it,
                    formatter,
                )
            },
        )
    }

    override suspend fun removeEntitiesByDate(cutoffMS: Long) {
        // DO NOT REMOVE EVENT BY DATE
    }

    override suspend fun fetchNextBatchAndMark(batchSize: Int): List<PublishingEvent> {
        return databaseInterface.getNextBatchAndMarkTransactionEvent(batchSize).map {
            it.transform(formatter, logger, platformType)
        }
    }

    override suspend fun getAll(): List<PublishingEvent> {
        return databaseInterface.getAllEvent().map {
            it.transform(formatter, logger, platformType)
        }
    }

    override suspend fun deleteEntities(dataList: List<PublishingEvent>) {
        if (dataList.isNotEmpty()) {
            databaseInterface.deleteByIdEvent(dataList.map { it.id })
        }
    }

    override suspend fun markEntitiesById(idList: List<Long>) {
        databaseInterface.markEvent(idList = idList)
    }

    override suspend fun unMarkEntitiesById(idList: List<Long>) {
        databaseInterface.unMarkEvent(idList = idList)
    }

    @VisibleForTesting
    override suspend fun getAllUnMark(): List<PublishingEvent> {
        return databaseInterface.getAllUnMarkEvent().map { it.transform(formatter, logger, platformType) }
    }
}
