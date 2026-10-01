package io.justtrack

import android.database.sqlite.SQLiteException
import io.justtrack.LogAggregator.LogSender
import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.DTOLogMetric
import io.justtrack.dtos.LogLevel
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import java.sql.SQLException
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

internal class LogAggregatorImplUnitTest {
    private val messageRepo = FakeRepository<LogStoreMessage> { id, entity -> LogStoreMessage(id, entity) }
    private val metricRepo = FakeRepository<LogStoreMetric> { id, entity -> LogStoreMetric(id, entity) }
    private val isTracking = AtomicBoolean(true)
    private val logAggregator = LogAggregatorImpl(
        messageRepo,
        metricRepo,
        TestLogger(),
        NetworkErrorLogger(),
        isTracking,
    )

    @Test
    fun addLogMessageSuspend_storesMessageWhenTrackingIsEnabled() = runTest {
        val message = testMessage("message")

        logAggregator.addLogMessageSuspend(message)

        Assert.assertEquals(listOf("message"), messageRepo.getAll().map { it.message })
    }

    @Test
    fun addLogMessageSuspend_storesMessageAfterSuspension() = runTest {
        val messageRepo = SuspendingRepository<LogStoreMessage> { id, entity -> LogStoreMessage(id, entity) }
        val logAggregator = createLogAggregator(messageRepo = messageRepo)

        logAggregator.addLogMessageSuspend(testMessage("message"))

        Assert.assertEquals(listOf("message"), messageRepo.getAll().map { it.message })
    }

    @Test
    fun addLogMessageSuspend_skipsMessageWhenTrackingIsDisabled() = runTest {
        isTracking.set(false)

        logAggregator.addLogMessageSuspend(testMessage("message"))

        Assert.assertTrue(messageRepo.getAll().isEmpty())
    }

    @Test
    fun addLogMessagesSuspend_skipsBatchWhenTrackingIsDisabled() = runTest {
        isTracking.set(false)

        logAggregator.addLogMessagesSuspend(listOf(testMessage("message-1"), testMessage("message-2")))

        Assert.assertTrue(messageRepo.getAll().isEmpty())
    }

    @Test
    fun addLogMetricSuspend_storesMetricWhenTrackingIsEnabled() = runTest {
        val metric = testMetric("metric")

        logAggregator.addLogMetricSuspend(metric)

        Assert.assertEquals(listOf("metric"), metricRepo.getAll().map { it.metric })
    }

    @Test
    fun addLogMetricSuspend_skipsSingleMetricWhenTrackingIsDisabled() = runTest {
        isTracking.set(false)

        logAggregator.addLogMetricSuspend(testMetric("metric"))

        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun addLogMetricSuspend_skipsMetricBatchWhenTrackingIsDisabled() = runTest {
        isTracking.set(false)

        logAggregator.addLogMetricSuspend(listOf(testMetric("metric-1"), testMetric("metric-2")))

        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun sendLogsAndMetricsSuspend_returnsWithoutSendingWhenRepositoriesAreEmpty() = runTest {
        val sender = CapturingLogSender(Result.success(Unit))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertTrue(sender.sentMessages.isEmpty())
        Assert.assertTrue(sender.sentMetrics.isEmpty())
    }

    @Test
    fun sendLogsAndMetricsSuspend_deletesMessagesAndMetricsAfterSuccessfulSend() = runTest {
        logAggregator.addLogMessagesSuspend(listOf(testMessage("message-1"), testMessage("message-2")))
        logAggregator.addLogMetricSuspend(listOf(testMetric("metric-1"), testMetric("metric-2")))
        val sender = CapturingLogSender(Result.success(Unit))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(listOf("message-1", "message-2"), sender.sentMessages.single().map { it.message })
        Assert.assertEquals(listOf("metric-1", "metric-2"), sender.sentMetrics.single().map { it.metric })
        Assert.assertTrue(messageRepo.getAll().isEmpty())
        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun sendLogsAndMetricsSuspend_deletesAfterSuspendingSuccessfulSend() = runTest {
        val messageRepo = SuspendingRepository<LogStoreMessage> { id, entity -> LogStoreMessage(id, entity) }
        val metricRepo = SuspendingRepository<LogStoreMetric> { id, entity -> LogStoreMetric(id, entity) }
        val logAggregator = createLogAggregator(messageRepo = messageRepo, metricRepo = metricRepo)
        logAggregator.addLogMessageSuspend(testMessage("message"))
        logAggregator.addLogMetricSuspend(testMetric("metric"))
        val sender = SuspendingLogSender(Result.success(Unit))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(listOf("message"), sender.sentMessages.single().map { it.message })
        Assert.assertEquals(listOf("metric"), sender.sentMetrics.single().map { it.metric })
        Assert.assertTrue(messageRepo.getAll().isEmpty())
        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun sendLogsAndMetricsSuspend_sendsAndDeletesMessagesWhenMetricsAreEmpty() = runTest {
        logAggregator.addLogMessageSuspend(testMessage("message"))
        val sender = CapturingLogSender(Result.success(Unit))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(listOf("message"), sender.sentMessages.single().map { it.message })
        Assert.assertTrue(sender.sentMetrics.single().isEmpty())
        Assert.assertTrue(messageRepo.getAll().isEmpty())
        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun sendLogsAndMetricsSuspend_sendsAndDeletesMetricsWhenMessagesAreEmpty() = runTest {
        logAggregator.addLogMetricSuspend(testMetric("metric"))
        val sender = CapturingLogSender(Result.success(Unit))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertTrue(sender.sentMessages.single().isEmpty())
        Assert.assertEquals(listOf("metric"), sender.sentMetrics.single().map { it.metric })
        Assert.assertTrue(messageRepo.getAll().isEmpty())
        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun sendLogsAndMetricsSuspend_unmarksMessagesAndMetricsAfterFailedSend() = runTest {
        logAggregator.addLogMessageSuspend(testMessage("message"))
        logAggregator.addLogMetricSuspend(testMetric("metric"))
        val sender = CapturingLogSender(Result.failure(RuntimeException("failed")))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(MAX_SEND_CALLS, sender.sentMessages.size)
        Assert.assertEquals(listOf("message"), sender.sentMessages.first().map { it.message })
        Assert.assertEquals(listOf("metric"), sender.sentMetrics.first().map { it.metric })
        Assert.assertEquals(listOf("message"), messageRepo.getAllUnMark().map { it.message })
        Assert.assertEquals(listOf("metric"), metricRepo.getAllUnMark().map { it.metric })
    }

    @Test
    fun sendLogsAndMetricsSuspend_unmarksAfterSuspendingFailedSend() = runTest {
        val messageRepo = SuspendingRepository<LogStoreMessage> { id, entity -> LogStoreMessage(id, entity) }
        val metricRepo = SuspendingRepository<LogStoreMetric> { id, entity -> LogStoreMetric(id, entity) }
        val logAggregator = createLogAggregator(messageRepo = messageRepo, metricRepo = metricRepo)
        logAggregator.addLogMessageSuspend(testMessage("message"))
        logAggregator.addLogMetricSuspend(testMetric("metric"))
        val sender = SuspendingLogSender(Result.failure(RuntimeException("failed")))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(MAX_SEND_CALLS, sender.sentMessages.size)
        Assert.assertEquals(listOf("message"), messageRepo.getAllUnMark().map { it.message })
        Assert.assertEquals(listOf("metric"), metricRepo.getAllUnMark().map { it.metric })
    }

    @Test
    fun sendLogsAndMetricsSuspend_unmarksMessagesAfterFailedSendWhenMetricsAreEmpty() = runTest {
        logAggregator.addLogMessageSuspend(testMessage("message"))
        val sender = CapturingLogSender(Result.failure(RuntimeException("failed")))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(MAX_SEND_CALLS, sender.sentMessages.size)
        Assert.assertEquals(listOf("message"), sender.sentMessages.first().map { it.message })
        Assert.assertTrue(sender.sentMetrics.first().isEmpty())
        Assert.assertEquals(listOf("message"), messageRepo.getAllUnMark().map { it.message })
        Assert.assertTrue(metricRepo.getAllUnMark().isEmpty())
    }

    @Test
    fun sendLogsAndMetricsSuspend_unmarksMetricsAfterFailedSendWhenMessagesAreEmpty() = runTest {
        logAggregator.addLogMetricSuspend(testMetric("metric"))
        val sender = CapturingLogSender(Result.failure(RuntimeException("failed")))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(MAX_SEND_CALLS, sender.sentMetrics.size)
        Assert.assertTrue(sender.sentMessages.first().isEmpty())
        Assert.assertEquals(listOf("metric"), sender.sentMetrics.first().map { it.metric })
        Assert.assertTrue(messageRepo.getAllUnMark().isEmpty())
        Assert.assertEquals(listOf("metric"), metricRepo.getAllUnMark().map { it.metric })
    }

    @Test
    fun sendLogsAndMetricsSuspend_handlesSenderException() = runTest {
        logAggregator.addLogMessageSuspend(testMessage("message"))
        val sender = ThrowingLogSender(RuntimeException("send failed"))

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertEquals(1, sender.sendCount)
        Assert.assertTrue(messageRepo.getAllUnMark().isEmpty())
    }

    @Test
    fun addLogMessageSuspend_handlesRepositoryException() = runTest {
        val messageRepo = ThrowingRepository<LogStoreMessage>(storeEntityException = SQLException("store failed"))
        val logAggregator = createLogAggregator(messageRepo = messageRepo)

        logAggregator.addLogMessageSuspend(testMessage("message"))

        Assert.assertTrue(messageRepo.getAll().isEmpty())
    }

    @Test
    fun addLogMessagesSuspend_handlesRepositoryException() = runTest {
        val messageRepo = ThrowingRepository<LogStoreMessage>(storeEntitiesException = RuntimeException("store failed"))
        val logAggregator = createLogAggregator(messageRepo = messageRepo)

        logAggregator.addLogMessagesSuspend(listOf(testMessage("message")))

        Assert.assertTrue(messageRepo.getAll().isEmpty())
    }

    @Test
    fun addLogMetricSuspend_handlesRepositoryException() = runTest {
        val metricRepo = ThrowingRepository<LogStoreMetric>(storeEntityException = RuntimeException("store failed"))
        val logAggregator = createLogAggregator(metricRepo = metricRepo)

        logAggregator.addLogMetricSuspend(testMetric("metric"))

        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun addLogMetricsSuspend_handlesRepositoryException() = runTest {
        val metricRepo = ThrowingRepository<LogStoreMetric>(storeEntitiesException = RuntimeException("store failed"))
        val logAggregator = createLogAggregator(metricRepo = metricRepo)

        logAggregator.addLogMetricSuspend(listOf(testMetric("metric")))

        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    @Test
    fun deleteEntities_handlesRepositoryException() = runTest {
        val messageRepo = ThrowingRepository<LogStoreMessage>(deleteException = RuntimeException("delete failed"))
        val logAggregator = createLogAggregator(messageRepo = messageRepo)

        logAggregator.deleteEntities(listOf(LogStoreMessage(1, testMessage("message"))), emptyList())
    }

    @Test
    fun removeOldEntitiesByCutoffDate_removesFromBothRepositories() = runTest {
        val cutoff = 123L

        logAggregator.removeOldEntitiesByCutoffDate(cutoff)

        Assert.assertTrue(messageRepo.removedCutoffs.contains(cutoff))
        Assert.assertTrue(metricRepo.removedCutoffs.contains(cutoff))
    }

    @Test
    fun removeOldEntitiesByCutoffDate_handlesRepositoryException() = runTest {
        val messageRepo = ThrowingRepository<LogStoreMessage>(removeException = RuntimeException("remove failed"))
        val logAggregator = createLogAggregator(messageRepo = messageRepo)

        logAggregator.removeOldEntitiesByCutoffDate(123L)
    }

    @Test
    fun sendLogsAndMetricsSuspend_handlesFetchException() = runTest {
        val messageRepo = ThrowingRepository<LogStoreMessage>(fetchException = RuntimeException("fetch failed"))
        val sender = CapturingLogSender(Result.success(Unit))
        val logAggregator = createLogAggregator(messageRepo = messageRepo)

        logAggregator.sendLogsAndMetricsSuspend(sender)

        Assert.assertTrue(sender.sentMessages.isEmpty())
    }

    @Test
    fun close_closesBothRepositories() {
        logAggregator.close()

        Assert.assertTrue(messageRepo.isClosed)
        Assert.assertTrue(metricRepo.isClosed)
    }

    @Test
    fun coroutineScope_isInitialized() {
        Assert.assertNotNull(logAggregator.coroutineScope)
    }

    @Test
    fun close_handlesCoroutineScopeCancelException() {
        val messageRepo = FakeRepository<LogStoreMessage> { id, entity -> LogStoreMessage(id, entity) }
        val metricRepo = FakeRepository<LogStoreMetric> { id, entity -> LogStoreMetric(id, entity) }
        val logAggregator = createLogAggregator(messageRepo = messageRepo, metricRepo = metricRepo)
        logAggregator.setCoroutineScopeForTesting(
            object : CoroutineScope {
                override val coroutineContext: CoroutineContext
                    get() = throw CoroutineScopeCancelTestException()
            },
        )

        logAggregator.close()

        Assert.assertTrue(messageRepo.isClosed)
        Assert.assertTrue(metricRepo.isClosed)
    }

    @Test
    fun handleException_logsSqlAndCancellationExceptionsToLogger() {
        val logger = CapturingLogger()
        val logAggregator = createLogAggregator(logger = logger, networkErrorLogger = mock())

        logAggregator.handleException("sqlite", SQLiteException("sqlite failed"))
        logAggregator.handleException("sql", SQLException("sql failed"))
        logAggregator.handleException("cancel", CancellationException("cancelled"))

        Assert.assertEquals(
            listOf(
                "LogAggregator Failed, sqlite: SQL error ",
                "LogAggregator Failed, sql: SQL error ",
                "LogAggregator Failed, cancel: got cancel ",
            ),
            logger.warningMessages,
        )
    }

    @Test
    fun handleException_delegatesOtherExceptionsToNetworkErrorLogger() {
        val networkErrorLogger = mock<NetworkErrorLogger>()
        val exception = RuntimeException("failed")
        val logAggregator = createLogAggregator(networkErrorLogger = networkErrorLogger)

        logAggregator.handleException("runtime", exception)

        verify(networkErrorLogger).logException(
            same(logAggregator.loggerForTesting),
            same(exception),
            eq("LogAggregator Failed, runtime: error "),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun publicAsyncMethods_executeOnConfiguredDispatcher() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val messageRepo = FakeRepository<LogStoreMessage> { id, entity -> LogStoreMessage(id, entity) }
        val metricRepo = FakeRepository<LogStoreMetric> { id, entity -> LogStoreMetric(id, entity) }
        val logAggregator = createLogAggregator(
            messageRepo = messageRepo,
            metricRepo = metricRepo,
            coroutineDispatcher = dispatcher,
        )

        logAggregator.addLogMessage(testMessage("message"))
        logAggregator.addLogMetric(testMetric("metric"))
        advanceUntilIdle()
        logAggregator.sendLogsAndMetrics(CapturingLogSender(Result.success(Unit)))
        advanceUntilIdle()

        Assert.assertTrue(messageRepo.getAll().isEmpty())
        Assert.assertTrue(metricRepo.getAll().isEmpty())
    }

    private fun testMessage(message: String) = DTOLogMessage(
        LogLevel.DEBUG,
        message,
        JSONObject(),
        Date(1_700_000_000_000L),
    )

    private fun testMetric(metric: String) = DTOLogMetric(
        metric,
        JSONObject(),
        1.0,
        "count",
        Date(1_700_000_000_000L),
    )

    private class CapturingLogSender(private val result: Result<Unit>) : LogSender {
        val sentMessages = mutableListOf<List<DTOLogMessage>>()
        val sentMetrics = mutableListOf<List<DTOLogMetric>>()

        override suspend fun sendLogsAndMetrics(messages: Collection<DTOLogMessage>, metrics: Collection<DTOLogMetric>): Result<Unit> {
            sentMessages.add(messages.toList())
            sentMetrics.add(metrics.toList())
            return result
        }
    }

    private class SuspendingLogSender(private val result: Result<Unit>) : LogSender {
        val sentMessages = mutableListOf<List<DTOLogMessage>>()
        val sentMetrics = mutableListOf<List<DTOLogMetric>>()

        override suspend fun sendLogsAndMetrics(messages: Collection<DTOLogMessage>, metrics: Collection<DTOLogMetric>): Result<Unit> {
            yield()
            sentMessages.add(messages.toList())
            sentMetrics.add(metrics.toList())
            return result
        }
    }

    private class ThrowingLogSender(private val exception: Throwable) : LogSender {
        var sendCount = 0

        override suspend fun sendLogsAndMetrics(messages: Collection<DTOLogMessage>, metrics: Collection<DTOLogMetric>): Result<Unit> {
            sendCount++
            throw exception
        }
    }

    private class CoroutineScopeCancelTestException : Exception("cancel failed")

    private class CapturingLogger : Logger {
        val warningMessages = mutableListOf<String>()

        override val fallback: Logger
            get() = this

        override fun debug(message: String, vararg fields: LoggerFields) = Unit

        override fun info(message: String, vararg fields: LoggerFields) = Unit

        override fun warn(message: String, vararg fields: LoggerFields) {
            warningMessages.add(message)
        }

        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
            warningMessages.add(message)
        }

        override fun error(message: String, vararg fields: LoggerFields) = Unit

        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit

        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
    }

    private class FakeRepository<T : LogStoreDatum>(
        private val copyWithId: (Long, T) -> T,
    ) : EntityRepository<T> {
        private val entities = linkedMapOf<Long, T>()
        private val markedIds = mutableSetOf<Long>()
        private var nextId = 1L
        val removedCutoffs = mutableListOf<Long>()
        var isClosed = false

        override suspend fun storeEntity(data: T): Long? {
            val id = nextId++
            entities[id] = copyWithId(id, data)
            return id
        }

        override suspend fun storeEntities(dataList: List<T>) {
            dataList.forEach { storeEntity(it) }
        }

        override suspend fun removeEntitiesByDate(cutoffMS: Long) {
            removedCutoffs.add(cutoffMS)
        }

        override suspend fun fetchNextBatchAndMark(batchSize: Int): List<T> {
            val batch = entities.filterKeys { it !in markedIds }.values.take(batchSize)
            markedIds.addAll(batch.map { it.id })
            return batch
        }

        override suspend fun markEntitiesById(idList: List<Long>) {
            markedIds.addAll(idList)
        }

        override suspend fun unMarkEntitiesById(idList: List<Long>) {
            markedIds.removeAll(idList.toSet())
        }

        override suspend fun deleteEntities(dataList: List<T>) {
            dataList.forEach {
                entities.remove(it.id)
                markedIds.remove(it.id)
            }
        }

        override suspend fun getAll(): List<T> = entities.values.toList()

        override suspend fun getAllUnMark(): List<T> = entities.filterKeys { it !in markedIds }.values.toList()

        override fun close() {
            isClosed = true
        }
    }

    private class SuspendingRepository<T : LogStoreDatum>(
        private val copyWithId: (Long, T) -> T,
    ) : EntityRepository<T> {
        private val entities = linkedMapOf<Long, T>()
        private val markedIds = mutableSetOf<Long>()
        private var nextId = 1L
        var isClosed = false

        override suspend fun storeEntity(data: T): Long? {
            yield()
            val id = nextId++
            entities[id] = copyWithId(id, data)
            return id
        }

        override suspend fun storeEntities(dataList: List<T>) {
            yield()
            dataList.forEach { storeEntity(it) }
        }

        override suspend fun removeEntitiesByDate(cutoffMS: Long) {
            yield()
        }

        override suspend fun fetchNextBatchAndMark(batchSize: Int): List<T> {
            yield()
            val batch = entities.filterKeys { it !in markedIds }.values.take(batchSize)
            markedIds.addAll(batch.map { it.id })
            return batch
        }

        override suspend fun markEntitiesById(idList: List<Long>) {
            yield()
            markedIds.addAll(idList)
        }

        override suspend fun unMarkEntitiesById(idList: List<Long>) {
            yield()
            markedIds.removeAll(idList.toSet())
        }

        override suspend fun deleteEntities(dataList: List<T>) {
            yield()
            dataList.forEach {
                entities.remove(it.id)
                markedIds.remove(it.id)
            }
        }

        override suspend fun getAll(): List<T> = entities.values.toList()

        override suspend fun getAllUnMark(): List<T> = entities.filterKeys { it !in markedIds }.values.toList()

        override fun close() {
            isClosed = true
        }
    }

    private class ThrowingRepository<T : LogStoreDatum>(
        private val storeEntityException: Exception? = null,
        private val storeEntitiesException: Exception? = null,
        private val removeException: Exception? = null,
        private val fetchException: Exception? = null,
        private val deleteException: Exception? = null,
    ) : EntityRepository<T> {
        override suspend fun storeEntity(data: T): Long? {
            storeEntityException?.let { throw it }
            return null
        }

        override suspend fun storeEntities(dataList: List<T>) {
            storeEntitiesException?.let { throw it }
        }

        override suspend fun removeEntitiesByDate(cutoffMS: Long) {
            removeException?.let { throw it }
        }

        override suspend fun fetchNextBatchAndMark(batchSize: Int): List<T> {
            fetchException?.let { throw it }
            return emptyList()
        }

        override suspend fun markEntitiesById(idList: List<Long>) = Unit

        override suspend fun unMarkEntitiesById(idList: List<Long>) = Unit

        override suspend fun deleteEntities(dataList: List<T>) {
            deleteException?.let { throw it }
        }

        override suspend fun getAll(): List<T> = emptyList()

        override suspend fun getAllUnMark(): List<T> = emptyList()

        override fun close() = Unit
    }

    private fun createLogAggregator(
        messageRepo: EntityRepository<LogStoreMessage> = FakeRepository { id, entity -> LogStoreMessage(id, entity) },
        metricRepo: EntityRepository<LogStoreMetric> = FakeRepository { id, entity -> LogStoreMetric(id, entity) },
        coroutineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        logger: Logger = TestLogger(),
        networkErrorLogger: NetworkErrorLogger = NetworkErrorLogger(),
        isTracking: AtomicBoolean = AtomicBoolean(true),
    ): LogAggregatorImpl {
        return LogAggregatorImpl(
            messageRepo,
            metricRepo,
            coroutineDispatcher,
            logger,
            networkErrorLogger,
            isTracking,
        )
    }

    private companion object {
        private const val MAX_SEND_CALLS = 10
    }

    private val LogAggregatorImpl.loggerForTesting: Logger
        get() = javaClass.getDeclaredField("logger").let { field ->
            field.isAccessible = true
            field.get(this) as Logger
        }

    private fun LogAggregatorImpl.setCoroutineScopeForTesting(coroutineScope: CoroutineScope) {
        javaClass.getDeclaredField("coroutineScope").let { field ->
            field.isAccessible = true
            field.set(this, coroutineScope)
        }
    }
}
