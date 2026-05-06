package io.justtrack

internal interface DatabaseMetricInterface : AutoCloseable {
    suspend fun insertMetric(metric: LogMetricEntity): Long?

    suspend fun insertMetrics(metrics: List<LogMetricEntity>): List<Long?>

    suspend fun deleteByIdMetric(idList: List<Long>): Boolean

    suspend fun deleteByDateMetric(cutoffMS: Long): Boolean

    suspend fun markMetric(idList: List<Long>): Boolean

    suspend fun unMarkMetric(idList: List<Long>): Boolean

    suspend fun getNextBatchAndMarkTransactionMetric(batchSize: Int = 100): List<LogMetricEntity>

    suspend fun getAllMetric(): List<LogMetricEntity>

    suspend fun getAllUnMarkMetric(): List<LogMetricEntity>

    suspend fun nukeTableMetric(): Boolean
}
