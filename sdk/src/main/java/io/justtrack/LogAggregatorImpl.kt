package io.justtrack

import android.database.sqlite.SQLiteException
import androidx.annotation.VisibleForTesting
import io.justtrack.LogAggregator.LogSender
import io.justtrack.log.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicBoolean

internal class LogAggregatorImpl constructor(
    private val messageRepo: EntityRepository<LogStoreMessage>,
    private val metricRepo: EntityRepository<LogStoreMetric>,
    coroutineDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val networkErrorLogger: NetworkErrorLogger,
    private val isTracking: AtomicBoolean,
) : LogAggregator, AutoCloseable {
    internal constructor(
        messageRepo: EntityRepository<LogStoreMessage>,
        metricRepo: EntityRepository<LogStoreMetric>,
        logger: Logger,
        networkErrorLogger: NetworkErrorLogger,
        isTracking: AtomicBoolean,
    ) : this(messageRepo, metricRepo, Dispatchers.IO, logger, networkErrorLogger, isTracking)

    private val coroutineCancelExceptionHandler =
        CoroutineExceptionHandler { _, exception ->
            handleException(
                "LogAggregatorImpl coroutine cancelled",
                exception,
            )
        }

    @VisibleForTesting
    internal val coroutineScope =
        CoroutineScope(coroutineDispatcher + coroutineCancelExceptionHandler)

    init {
        coroutineScope.launch {
            removeOldEntitiesByCutoffDate(System.currentTimeMillis() - MAX_RETENTION_MS)
        }
    }

    override fun addLogMessage(message: DTOLogMessage) {
        coroutineScope.launch {
            addLogMessageSuspend(LogMessageDatum(message))
        }
    }

    override fun addLogMetric(metric: DTOLogMetric) {
        coroutineScope.launch {
            addLogMetricSuspend(LogMetricDatum(metric))
        }
    }

    override fun sendLogsAndMetrics(sender: LogSender) {
        coroutineScope.launch {
            sendLogsAndMetricsSuspend(sender)
        }
    }

    override fun close() {
        try {
            coroutineScope.cancel(CancellationException())
        } catch (exception: java.lang.Exception) {
            handleException("Issue with coroutineScope cancel", exception)
        }
        messageRepo.close()
        metricRepo.close()
    }

    @VisibleForTesting
    @JvmName("addLogMessageSuspend")
    internal suspend fun addLogMessageSuspend(message: DTOLogMessage) = withContext(Dispatchers.IO) {
        if (!isTracking.get()) {
            return@withContext
        }
        try {
            messageRepo.storeEntity(LogStoreMessage(message))
        } catch (e: Exception) {
            handleException("Unable to store message to local database", e)
        }
    }

    @VisibleForTesting
    @JvmName("addLogMessagesSuspend")
    internal suspend fun addLogMessagesSuspend(messages: List<DTOLogMessage>) {
        if (!isTracking.get()) {
            return
        }
        try {
            messageRepo.storeEntities((messages.map { LogStoreMessage(it) }))
        } catch (e: Exception) {
            handleException("Unable to store messages to local database", e)
        }
    }

    @VisibleForTesting
    internal suspend fun sendLogsAndMetricsSuspend(sender: LogSender) {
        try {
            repeat(MAX_SEND_CALLS) {
                val messages: List<LogStoreMessage> = messageRepo.fetchNextBatchAndMark()
                val metrics: List<LogStoreMetric> = metricRepo.fetchNextBatchAndMark()

                if (messages.isEmpty() && metrics.isEmpty()) {
                    return
                }
                val result = sender.sendLogsAndMetrics(messages, metrics)

                if (result.isSuccess) {
                    deleteEntities(messages, metrics)
                } else {
                    handleException(
                        "Unable to send message (${messages.size}), metric (${metrics.size}) to server",
                        result.exceptionOrNull() ?: Throwable("Publishing messages and metrics failed with unknown exception"),
                    )

                    messageRepo.unMarkEntitiesById(messages.map { it.id })
                    metricRepo.unMarkEntitiesById(metrics.map { it.id })
                }
            }
        } catch (exception: Exception) {
            handleException("Unable to sendLogsAndMetricsSuspend", exception)
        }
    }

    @VisibleForTesting
    @JvmName("addLogMetricSuspend")
    internal suspend fun addLogMetricSuspend(metric: DTOLogMetric) {
        if (!isTracking.get()) {
            return
        }

        try {
            metricRepo.storeEntity(LogStoreMetric(metric))
        } catch (exception: Exception) {
            handleException("Unable to store metric to local database", exception)
        }
    }

    @VisibleForTesting
    @JvmName("addLogMetricSuspend")
    internal suspend fun addLogMetricSuspend(metric: List<DTOLogMetric>) {
        if (!isTracking.get()) {
            return
        }

        try {
            metricRepo.storeEntities((metric.map { LogStoreMetric(it) }))
        } catch (e: Exception) {
            handleException("Unable to store metrics to local database", e)
        }
    }

    private suspend fun deleteEntities(messages: List<LogStoreMessage>, metrics: List<LogStoreMetric>) {
        try {
            messageRepo.deleteEntities(messages)
            metricRepo.deleteEntities(metrics)
        } catch (exception: Exception) {
            handleException("Unable to delete message and metric", exception)
        }
    }

    private suspend fun removeOldEntitiesByCutoffDate(cutoffMS: Long) {
        try {
            messageRepo.removeEntitiesByDate(cutoffMS)
            metricRepo.removeEntitiesByDate(cutoffMS)
        } catch (exception: Exception) {
            handleException("Unable to delete old messages and metrics", exception)
        }
    }

    private fun handleException(message: String, exception: Throwable) {
        when (exception) {
            is SQLiteException, is SQLException -> {
                logger.warn("LogAggregator Failed, $message: SQL error ", exception)
            }

            is CancellationException -> {
                logger.warn("LogAggregator Failed, $message: got cancel ", exception)
            }

            else -> {
                networkErrorLogger.logException(logger, exception, "LogAggregator Failed, $message: error ")
            }
        }
    }

    companion object {
        private const val MAX_SEND_CALLS = 10
        private const val MAX_RETENTION_MS = 3 * 24 * 3600 * 1000L
    }
}
