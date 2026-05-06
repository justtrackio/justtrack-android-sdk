package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.log.Logger

internal class MetricRepositoryImpl constructor(
    private val formatter: Formatter,
    private val databaseInterface: DatabaseMetricInterface,
    private val logger: Logger,
) : EntityRepository<LogStoreMetric> {
    override fun close() {
        databaseInterface.close()
    }

    override suspend fun storeEntity(data: LogStoreMetric): Long? {
        return databaseInterface.insertMetric(LogMetricEntity(data, formatter))
    }

    override suspend fun storeEntities(dataList: List<LogStoreMetric>) {
        databaseInterface.insertMetrics(dataList.map { LogMetricEntity(it, formatter) })
    }

    override suspend fun removeEntitiesByDate(cutoffMS: Long) {
        databaseInterface.deleteByDateMetric(cutoffMS)
    }

    override suspend fun fetchNextBatchAndMark(batchSize: Int): List<LogStoreMetric> {
        val entityList: List<LogMetricEntity> =
            databaseInterface.getNextBatchAndMarkTransactionMetric(batchSize)
        return entityList.map {
            it.transform(formatter, logger)
        }
    }

    override suspend fun deleteEntities(dataList: List<LogStoreMetric>) {
        if (dataList.isNotEmpty()) {
            databaseInterface.deleteByIdMetric(dataList.map { it.id })
        }
    }

    override suspend fun markEntitiesById(idList: List<Long>) {
        databaseInterface.markMetric(idList = idList)
    }

    override suspend fun unMarkEntitiesById(idList: List<Long>) {
        databaseInterface.unMarkMetric(idList)
    }

    @VisibleForTesting
    override suspend fun getAll(): List<LogStoreMetric> {
        return databaseInterface.getAllMetric().map {
            it.transform(formatter, logger)
        }
    }

    @VisibleForTesting
    override suspend fun getAllUnMark(): List<LogStoreMetric> {
        return databaseInterface.getAllUnMarkMetric().map {
            it.transform(formatter, logger)
        }
    }
}
