package io.justtrack

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.justtrack.database.Database
import io.justtrack.database.DatabaseOperation
import io.justtrack.log.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal open class DatabaseInterface
@VisibleForTesting
internal constructor(
    val context: Context,
    consoleLogger: Logger,
    val database: Database,
) {
    internal constructor(context: Context, logger: Logger) : this(
        context,
        logger,
        Database(context, logger),
    )

    private val refCount = AtomicInteger(0)

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var currentOpChannel = OpChannel()
    private val consumeOperationChannel = Channel<OpChannel>()
    private var logger: Logger = consoleLogger

    init {
        coroutineScope.launch {
            consumeOperationChannel.consumeEach {
                consumeOpChannel(it.operationChannel, database)
                it.closeChannel.send(Unit)
            }
        }
    }

    @JvmName("setLogger")
    internal fun setLogger(logger: Logger) {
        this.logger = logger
        this.database.setLogger(logger)
    }

    fun openMessages(): DatabaseMessageInterface {
        val closer = open()

        return object : DatabaseMessageInterface {
            override suspend fun insertMessage(message: LogMessageEntity): Long? {
                return this@DatabaseInterface.insertMessage(message)
            }

            override suspend fun insertMessages(messages: List<LogMessageEntity>): List<Long?> {
                return this@DatabaseInterface.insertMessages(messages)
            }

            override suspend fun deleteByIdMessage(idList: List<Long>): Boolean {
                return this@DatabaseInterface.deleteByIdMessage(idList)
            }

            override suspend fun deleteByDateMessage(cutoffMS: Long): Boolean {
                return this@DatabaseInterface.deleteByDateMessage(cutoffMS)
            }

            override suspend fun markMessage(idList: List<Long>): Boolean {
                return this@DatabaseInterface.markMessage(idList)
            }

            override suspend fun unMarkMessage(idList: List<Long>): Boolean {
                return this@DatabaseInterface.unMarkMessage(idList)
            }

            override suspend fun getNextBatchAndMarkTransactionMessage(batchSize: Int): List<LogMessageEntity> {
                return this@DatabaseInterface.getNextBatchAndMarkTransactionMessage(batchSize)
            }

            override suspend fun getAllMessage(): List<LogMessageEntity> {
                return this@DatabaseInterface.getAllMessage()
            }

            override suspend fun getAllUnMarkMessage(): List<LogMessageEntity> {
                return this@DatabaseInterface.getAllUnMarkMessage()
            }

            override suspend fun nukeTableMessage(): Boolean {
                return this@DatabaseInterface.nukeTableMessage()
            }

            override fun close() {
                closer.close()
            }
        }
    }

    fun openMetrics(): DatabaseMetricInterface {
        val closer = open()

        return object : DatabaseMetricInterface {
            override suspend fun insertMetric(metric: LogMetricEntity): Long? {
                return this@DatabaseInterface.insertMetric(metric)
            }

            override suspend fun insertMetrics(metrics: List<LogMetricEntity>): List<Long?> {
                return this@DatabaseInterface.insertMetrics(metrics)
            }

            override suspend fun deleteByIdMetric(idList: List<Long>): Boolean {
                return this@DatabaseInterface.deleteByIdMetric(idList)
            }

            override suspend fun deleteByDateMetric(cutoffMS: Long): Boolean {
                return this@DatabaseInterface.deleteByDateMetric(cutoffMS)
            }

            override suspend fun markMetric(idList: List<Long>): Boolean {
                return this@DatabaseInterface.markMetric(idList)
            }

            override suspend fun unMarkMetric(idList: List<Long>): Boolean {
                return this@DatabaseInterface.unMarkMetric(idList)
            }

            override suspend fun getNextBatchAndMarkTransactionMetric(batchSize: Int): List<LogMetricEntity> {
                return this@DatabaseInterface.getNextBatchAndMarkTransactionMetric(batchSize)
            }

            override suspend fun getAllMetric(): List<LogMetricEntity> {
                return this@DatabaseInterface.getAllMetric()
            }

            override suspend fun getAllUnMarkMetric(): List<LogMetricEntity> {
                return this@DatabaseInterface.getAllUnMarkMetric()
            }

            override suspend fun nukeTableMetric(): Boolean {
                return this@DatabaseInterface.nukeTableMetric()
            }

            override fun close() {
                closer.close()
            }
        }
    }

    fun openEvents(): DatabaseEventInterface {
        val closer = open()

        return object : DatabaseEventInterface {
            override suspend fun insertEvent(event: UserEventEntity): Pair<Long, Long>? {
                return this@DatabaseInterface.insertEvent(event)
            }

            override suspend fun insertEvents(events: List<UserEventEntity>): List<Pair<Long, Long>?> {
                return this@DatabaseInterface.insertEvents(events)
            }

            override suspend fun deleteByIdEvent(idList: List<Long>): Boolean {
                return this@DatabaseInterface.deleteByIdEvent(idList)
            }

            override suspend fun deleteByDateEvent(cutoffMS: Long): Boolean {
                return this@DatabaseInterface.deleteByDateEvent(cutoffMS)
            }

            override suspend fun markEvent(idList: List<Long>): Boolean {
                return this@DatabaseInterface.markEvent(idList)
            }

            override suspend fun unMarkEvent(idList: List<Long>): Boolean {
                return this@DatabaseInterface.unMarkEvent(idList)
            }

            override suspend fun getNextBatchAndMarkTransactionEvent(batchSize: Int): List<UserEventEntity> {
                return this@DatabaseInterface.getNextBatchAndMarkTransactionEvent(batchSize)
            }

            override suspend fun getAllEvent(): List<UserEventEntity> {
                return this@DatabaseInterface.getAllEvent()
            }

            override suspend fun getAllUnMarkEvent(): List<UserEventEntity> {
                return this@DatabaseInterface.getAllUnMarkEvent()
            }

            override suspend fun nukeTableEvent(): Boolean {
                return this@DatabaseInterface.nukeTableEvent()
            }

            override fun close() {
                closer.close()
            }
        }
    }

    fun openAttribution(): DatabaseAttributionInterface {
        val closer = open()

        return object : DatabaseAttributionInterface {
            override suspend fun setIntegrityTokenSent(isSent: Boolean): Boolean {
                return this@DatabaseInterface.setIntegrityTokenSent(isSent)
            }

            override suspend fun isIntegrityTokenSent(): Boolean {
                return this@DatabaseInterface.isIntegrityTokenSent()
            }

            override suspend fun setIntegritySecret(token: String): Boolean {
                return this@DatabaseInterface.setIntegritySecret(token)
            }

            override suspend fun getIntegritySecret(): String? {
                return this@DatabaseInterface.getIntegritySecret()
            }

            override suspend fun getAttributionTimestamps(): AttributionTimestamps? {
                return this@DatabaseInterface.getAttributionTimestamps()
            }

            override suspend fun setAttributionFinished(response: AttributionResponse): Boolean {
                return this@DatabaseInterface.setAttributionFinished(response)
            }

            override suspend fun setLastOpen(currentMs: Long): Boolean {
                return this@DatabaseInterface.setLastOpen(currentMs)
            }

            override suspend fun getStoredOutput(): AttributionOutput? {
                return this@DatabaseInterface.getStoredOutput()
            }

            override suspend fun getAppVersionUpdateInfo(currentApplicationVersion: ApplicationVersion): AppVersionUpdateInfo? {
                return this@DatabaseInterface.getAppVersionUpdateInfo(currentApplicationVersion)
            }

            override suspend fun getInstallId(): String? {
                return this@DatabaseInterface.getInstallId()
            }

            override suspend fun getUserId(): String? {
                return this@DatabaseInterface.getUserId()
            }

            override suspend fun setInstallId(installId: String): Boolean {
                return this@DatabaseInterface.setInstallId(installId)
            }

            override suspend fun setUserId(userId: String): Boolean {
                return this@DatabaseInterface.setUserId(userId)
            }

            override fun close() {
                closer.close()
            }
        }
    }

    private fun open(): AutoCloseable {
        val openCount = refCount.incrementAndGet()
        if (openCount == 1) {
            val opChannel = OpChannel()
            currentOpChannel = opChannel
            coroutineScope.launch {
                consumeOperationChannel.send(opChannel)
            }
        }

        val closed = AtomicBoolean(false)
        val notClosedError = RuntimeException("Failed to call close")

        return object : AutoCloseable {
            protected fun finalize() {
                if (!closed.get()) {
                    logger.warn("A database interface was not properly closed", notClosedError)
                    close()
                }
            }

            override fun close() {
                if (!closed.getAndSet(true)) {
                    if (refCount.decrementAndGet() == 0) {
                        // copy the channel to a local variable - if we open the db again
                        // before we close the channel, we would close the fresh channel when
                        // closing mainOperationChannel directly
                        val channelToClose = currentOpChannel
                        runBlocking {
                            channelToClose.operationChannel.close()
                            channelToClose.closeChannel.receive()
                        }
                    }
                }
            }
        }
    }

    private suspend fun consumeOpChannel(opChannel: Channel<DatabaseOperation>, database: Database) {
        try {
            opChannel.consumeEach { operation ->
                executeOperation(database, operation)
            }
        } catch (e: Exception) {
            logger.warn("Database coroutine failed with error", e)
        } finally {
            database.close()
        }
    }

    private suspend fun insertMessage(message: LogMessageEntity): Long? = addOperationWithResult<Long?>(null) { resultChannel ->
        DatabaseOperation.InsertMessage(message, resultChannel)
    }

    private suspend fun insertMessages(messages: List<LogMessageEntity>): List<Long?> {
        val results = ArrayList<Long?>()
        messages.forEach {
            results.add(insertMessage(it))
        }
        return results
    }

    private suspend fun insertMetric(metric: LogMetricEntity): Long? = addOperationWithResult<Long?>(null) { resultChannel ->
        DatabaseOperation.InsertMetric(metric, resultChannel)
    }

    private suspend fun insertMetrics(metrics: List<LogMetricEntity>): List<Long?> {
        val results = ArrayList<Long?>()
        metrics.forEach {
            results.add(insertMetric(it))
        }
        return results
    }

    /**
     * Insert new event to SQLite, when the SESSION_END_EVENT is received this will set "hasReceivedSessionEnd"
     * as true. By doing so, the channel will still be running but reject any new databaseOperation.
     */
    private suspend fun insertEvent(event: UserEventEntity): Pair<Long, Long>? = addOperationWithResult<Pair<Long, Long>?>(null) { resultChannel ->
        DatabaseOperation.InsertEvent(event, resultChannel)
    }

    private suspend fun insertEvents(events: List<UserEventEntity>): List<Pair<Long, Long>?> {
        val results = ArrayList<Pair<Long, Long>?>()
        events.forEach {
            results.add(insertEvent(it))
        }
        return results
    }

    private suspend fun getNextBatchAndMarkTransactionMessage(batchSize: Int): List<LogMessageEntity> =
        addOperationWithResult(emptyList()) { resultChannel ->
            DatabaseOperation.GetNextBatchAndMarkMessage(
                batchSize,
                resultChannel,
            )
        }

    private suspend fun getNextBatchAndMarkTransactionMetric(batchSize: Int): List<LogMetricEntity> =
        addOperationWithResult(emptyList()) { resultChannel ->
            DatabaseOperation.GetNextBatchAndMarkMetric(
                batchSize,
                resultChannel,
            )
        }

    private suspend fun getNextBatchAndMarkTransactionEvent(batchSize: Int): List<UserEventEntity> =
        addOperationWithResult(emptyList()) { resultChannel ->
            DatabaseOperation.GetNextBatchAndMarkEvent(
                batchSize,
                resultChannel,
            )
        }

    private suspend fun deleteByIdMessage(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.DeleteMessage(
            idList,
            null,
            resultChannel,
        )
    }

    private suspend fun deleteByIdMetric(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.DeleteMetric(
            idList,
            null,
            resultChannel,
        )
    }

    private suspend fun deleteByIdEvent(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.DeleteEvent(
            idList,
            null,
            resultChannel,
        )
    }

    private suspend fun deleteByDateMessage(cutoffMS: Long): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.DeleteMessage(
            null,
            cutoffMS,
            resultChannel,
        )
    }

    private suspend fun deleteByDateMetric(cutoffMS: Long): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.DeleteMetric(
            null,
            cutoffMS,
            resultChannel,
        )
    }

    private suspend fun deleteByDateEvent(cutoffMS: Long): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.DeleteEvent(
            null,
            cutoffMS,
            resultChannel,
        )
    }

    private suspend fun markMessage(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.MarkMessage(
            idList,
            resultChannel,
        )
    }

    private suspend fun markMetric(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.MarkMetric(
            idList,
            resultChannel,
        )
    }

    private suspend fun markEvent(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.MarkEvent(
            idList,
            resultChannel,
        )
    }

    private suspend fun unMarkMessage(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.UnMarkMessage(
            idList,
            resultChannel,
        )
    }

    private suspend fun unMarkMetric(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.UnMarkMetric(
            idList,
            resultChannel,
        )
    }

    private suspend fun unMarkEvent(idList: List<Long>): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.UnMarkEvent(
            idList,
            resultChannel,
        )
    }

    private suspend fun getAllMessage(): List<LogMessageEntity> = addOperationWithResult(emptyList()) { resultChannel ->
        DatabaseOperation.GetAllMessage(resultChannel = resultChannel)
    }

    private suspend fun getAllMetric(): List<LogMetricEntity> = addOperationWithResult(emptyList()) { resultChannel ->
        DatabaseOperation.GetAllMetric(resultChannel = resultChannel)
    }

    private suspend fun getAllEvent(): List<UserEventEntity> = addOperationWithResult(emptyList()) { resultChannel ->
        DatabaseOperation.GetAllEvent(resultChannel = resultChannel)
    }

    private suspend fun getAllUnMarkMessage(): List<LogMessageEntity> = addOperationWithResult(emptyList()) { resultChannel ->
        DatabaseOperation.GetAllMessage(unMarkOnly = true, resultChannel = resultChannel)
    }

    private suspend fun getAllUnMarkMetric(): List<LogMetricEntity> = addOperationWithResult(emptyList()) { resultChannel ->
        DatabaseOperation.GetAllMetric(unMarkOnly = true, resultChannel = resultChannel)
    }

    private suspend fun getAllUnMarkEvent(): List<UserEventEntity> = addOperationWithResult(emptyList()) { resultChannel ->
        DatabaseOperation.GetAllEvent(unMarkOnly = true, resultChannel = resultChannel)
    }

    private suspend fun setIntegrityTokenSent(isSent: Boolean): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.SetIntegrityTokenSent(isSent = isSent, resultChannel = resultChannel)
    }

    private suspend fun isIntegrityTokenSent(): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.IsIntegrityTokenSent(resultChannel = resultChannel)
    }

    private suspend fun setIntegritySecret(secret: String): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.SetIntegritySecret(secret = secret, resultChannel = resultChannel)
    }

    private suspend fun getIntegritySecret(): String? = addOperationWithResult(null as String?) { resultChannel ->
        DatabaseOperation.GetIntegritySecret(resultChannel = resultChannel)
    }

    private suspend fun setAttributionFinished(response: AttributionResponse): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.SetAttributionFinished(
            resultChannel = resultChannel,
            response = response,
        )
    }

    private suspend fun getStoredOutput(): AttributionOutput? = addOperationWithResult(null as AttributionOutput?) { resultChannel ->
        DatabaseOperation.GetStoredOutput(resultChannel = resultChannel)
    }

    private suspend fun getAttributionTimestamps(): AttributionTimestamps? = addOperationWithResult(null as AttributionTimestamps?) { resultChannel ->
        DatabaseOperation.GetAttributionTimestamps(resultChannel = resultChannel)
    }

    private suspend fun setLastOpen(currentMs: Long): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.SetLastOpen(resultChannel = resultChannel, currentMs = currentMs)
    }

    private suspend fun getAppVersionUpdateInfo(currentApplicationVersion: ApplicationVersion): AppVersionUpdateInfo? =
        addOperationWithResult(null as AppVersionUpdateInfo?) { resultChannel ->
            DatabaseOperation.GetAppVersionUpdateInfo(resultChannel = resultChannel, currentApplicationVersion = currentApplicationVersion)
        }

    private suspend fun getInstallId(): String? = addOperationWithResult(null as String?) { resultChannel ->
        DatabaseOperation.GetInstallId(resultChannel = resultChannel)
    }

    private suspend fun getUserId(): String? = addOperationWithResult(null as String?) { resultChannel ->
        DatabaseOperation.GetUserId(resultChannel = resultChannel)
    }

    private suspend fun setInstallId(installId: String): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.SetInstallId(resultChannel = resultChannel, installId = installId)
    }

    private suspend fun setUserId(userId: String): Boolean = addOperationWithResult(false) { resultChannel ->
        DatabaseOperation.SetUserId(resultChannel = resultChannel, userId = userId)
    }

    private suspend fun nukeTableMessage(): Boolean = addOperation(DatabaseOperation.NukeMessage)

    private suspend fun nukeTableMetric(): Boolean = addOperation(DatabaseOperation.NukeMetric)

    private suspend fun nukeTableEvent(): Boolean = addOperation(DatabaseOperation.NukeEvent)

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun addOperation(operation: DatabaseOperation): Boolean {
        val opChannel = currentOpChannel.operationChannel
        try {
            opChannel.send(operation)

            return true
        } catch (e: CancellationException) {
            // only do the check for closed channels here - if we first do the check, the channel
            // can still be closed between our check and us trying to send something
            if (opChannel.isClosedForSend) {
                return false
            }

            throw e
        }
    }

    private suspend fun <T> addOperationWithResult(default: T, makeOperation: (Channel<T>) -> DatabaseOperation): T {
        // give the channel a small capacity so the database doesn't block if we (for whatever reason)
        // can't immediately consume the result
        val resultChannel = Channel<T>(1)
        val operationSent = addOperation(makeOperation(resultChannel))
        return if (operationSent) {
            val result = resultChannel.receive()
            resultChannel.close()
            result
        } else {
            resultChannel.close()
            default
        }
    }

    private suspend fun executeOperation(database: Database, operation: DatabaseOperation) {
        when (operation) {
            is DatabaseOperation.InsertMessage -> {
                val id = database.insertMessage(operation.message)
                operation.resultChannel.send(id)
            }

            is DatabaseOperation.InsertMetric -> {
                val id = database.insertMetric(operation.metric)
                operation.resultChannel.send(id)
            }

            is DatabaseOperation.InsertEvent -> {
                val resultPair = database.insertEvent(operation.event)
                operation.resultChannel.send(resultPair)
            }

            is DatabaseOperation.DeleteMessage -> {
                val result = deleteMessage(database, operation.idList, operation.cutOffMS)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.DeleteMetric -> {
                val result = deleteMetric(database, operation.idList, operation.cutOffMS)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.DeleteEvent -> {
                val result = deleteEvent(database, operation.idList, operation.cutOffMS)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.MarkMessage -> {
                val result = doMarkMessage(database, operation.idList)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.MarkMetric -> {
                val result = doMarkMetric(database, operation.idList)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.MarkEvent -> {
                val result = doMarkEvent(database, operation.idList)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.UnMarkMessage -> {
                val result = doUnMarkMessage(database, operation.idList)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.UnMarkMetric -> {
                val result = doUnMarkMetric(database, operation.idList)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.UnMarkEvent -> {
                val result = doUnMarkEvent(database, operation.idList)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetNextBatchAndMarkMessage -> {
                val result = database.getNextBatchAndMarkTransactionMessage(operation.batchSize)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetNextBatchAndMarkMetric -> {
                val result =
                    database.getNextBatchAndMarkTransactionMetric(operation.batchSize)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetNextBatchAndMarkEvent -> {
                val result =
                    database.getNextBatchAndMarkTransactionEvent(operation.batchSize)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetAllMessage -> {
                val result = getAllMessages(database, operation.unMarkOnly)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetAllMetric -> {
                val result = getAllMetrics(database, operation.unMarkOnly)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetAllEvent -> {
                val result = getAllEvents(database, operation.unMarkOnly)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.SetIntegrityTokenSent -> {
                val result = setIntegrityTokenSent(database, operation.isSent)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.IsIntegrityTokenSent -> {
                val result = isIntegrityTokenSent(database)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.SetIntegritySecret -> {
                val result = setIntegritySecret(database, operation.secret)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetIntegritySecret -> {
                val result = getIntegritySecret(database)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.SetAttributionFinished -> {
                setAttributionFinished(database, operation.response)
                operation.resultChannel.send(true)
            }

            is DatabaseOperation.GetStoredOutput -> {
                val result = getStoredOutput(database)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetAttributionTimestamps -> {
                val result = getAttributionTimestamps(database)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.SetLastOpen -> {
                setLastOpen(database, operation.currentMs)
                operation.resultChannel.send(true)
            }

            is DatabaseOperation.GetAppVersionUpdateInfo -> {
                val result = getAppVersionUpdateInfo(database, operation.currentApplicationVersion)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetInstallId -> {
                val result = getInstallId(database)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.GetUserId -> {
                val result = getUserId(database)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.SetInstallId -> {
                val result = setInstallId(database, operation.installId)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.SetUserId -> {
                val result = setUserId(database, operation.userId)
                operation.resultChannel.send(result)
            }

            is DatabaseOperation.NukeMessage -> {
                database.nukeTableMessage()
            }

            is DatabaseOperation.NukeMetric -> {
                database.nukeTableMetric()
            }

            is DatabaseOperation.NukeEvent -> {
                database.nukeTableEvent()
            }
        }
    }

    private fun setIntegrityTokenSent(database: Database, isSent: Boolean): Boolean {
        return database.setIntegrityTokenSent(isSent)
    }

    private fun isIntegrityTokenSent(database: Database): Boolean {
        return database.isIntegrityTokenSent()
    }

    private fun setIntegritySecret(database: Database, secret: String): Boolean {
        return database.setIntegritySecret(secret)
    }

    private fun getIntegritySecret(database: Database): String? {
        return database.getIntegritySecret()
    }

    private fun setAttributionFinished(database: Database, response: AttributionResponse) {
        database.setAttributionFinished(response)
    }

    private fun getAttributionTimestamps(database: Database): AttributionTimestamps? {
        return database.getAttributionTimestamps()
    }

    private fun getStoredOutput(database: Database): AttributionOutput? {
        return database.getStoredOutput()
    }

    private fun setLastOpen(database: Database, currentMs: Long) {
        database.setLastOpen(currentMs)
    }

    private fun getAppVersionUpdateInfo(database: Database, currentApplicationVersion: ApplicationVersion): AppVersionUpdateInfo {
        return database.getAppVersionUpdateInfo(currentApplicationVersion)
    }

    private fun getInstallId(database: Database): String? {
        return database.getInstallId()
    }

    private fun setInstallId(database: Database, installId: String): Boolean {
        return database.setInstallId(installId)
    }

    private fun getUserId(database: Database): String? {
        return database.getUserId()
    }

    private fun setUserId(database: Database, userId: String): Boolean {
        return database.setUserid(userId)
    }

    private fun deleteMessage(database: Database, idList: List<Long>?, cutoffMS: Long?): Boolean {
        idList?.let {
            return database.deleteById(Database.MESSAGE_TABLE_NAME, it)
        }
        cutoffMS?.let {
            return database.deleteByDate(Database.MESSAGE_TABLE_NAME, it)
        }
        return false
    }

    private fun deleteMetric(database: Database, idList: List<Long>?, cutoffMS: Long?): Boolean {
        idList?.let {
            return database.deleteById(Database.METRIC_TABLE_NAME, it)
        }
        cutoffMS?.let {
            return database.deleteByDate(Database.METRIC_TABLE_NAME, it)
        }
        return false
    }

    private fun deleteEvent(database: Database, idList: List<Long>?, cutoffMS: Long?): Boolean {
        idList?.let {
            return database.deleteById(Database.EVENT_TABLE_NAME, it)
        }
        cutoffMS?.let {
            return database.deleteByDate(Database.EVENT_TABLE_NAME, it)
        }
        return false
    }

    private fun doMarkMessage(database: Database, idList: List<Long>): Boolean {
        return database.mark(Database.MESSAGE_TABLE_NAME, idList, System.currentTimeMillis())
    }

    private fun doMarkMetric(database: Database, idList: List<Long>): Boolean {
        return database.mark(Database.METRIC_TABLE_NAME, idList, System.currentTimeMillis())
    }

    private fun doMarkEvent(database: Database, idList: List<Long>): Boolean {
        return database.mark(Database.EVENT_TABLE_NAME, idList, System.currentTimeMillis())
    }

    private fun doUnMarkMessage(database: Database, idList: List<Long>): Boolean {
        return database.unMark(Database.MESSAGE_TABLE_NAME, idList)
    }

    private fun doUnMarkMetric(database: Database, idList: List<Long>): Boolean {
        return database.unMark(Database.METRIC_TABLE_NAME, idList)
    }

    private fun doUnMarkEvent(database: Database, idList: List<Long>): Boolean {
        return database.unMark(Database.EVENT_TABLE_NAME, idList)
    }

    private fun getAllMessages(database: Database, onlyUnMark: Boolean): List<LogMessageEntity> {
        return if (onlyUnMark) {
            database.getAllUnMark(Database.MESSAGE_TABLE_NAME) { cursor ->
                database.transformMessage(cursor)
            }
        } else {
            database.getAll(Database.MESSAGE_TABLE_NAME) { cursor ->
                database.transformMessage(cursor)
            }
        }
    }

    private fun getAllMetrics(database: Database, onlyUnMark: Boolean): List<LogMetricEntity> {
        return if (onlyUnMark) {
            database.getAllUnMark(Database.METRIC_TABLE_NAME) { cursor ->
                database.transformMetric(cursor)
            }
        } else {
            database.getAll(Database.METRIC_TABLE_NAME) { cursor ->
                database.transformMetric(cursor)
            }
        }
    }

    private fun getAllEvents(database: Database, onlyUnMark: Boolean): List<UserEventEntity> {
        return if (onlyUnMark) {
            database.getAllUnMark(Database.EVENT_TABLE_NAME) { cursor ->
                database.transformEvent(cursor)
            }
        } else {
            database.getAll(Database.EVENT_TABLE_NAME) { cursor ->
                database.transformEvent(cursor)
            }
        }
    }

    internal companion object {
        @Volatile
        private var instance: DatabaseInterface? = null

        @JvmStatic
        fun getInstance(context: Context, logger: Logger): DatabaseInterface {
            return instance ?: synchronized(this) {
                instance ?: DatabaseInterface(context, logger).also { instance = it }
            }
        }

        @VisibleForTesting
        @JvmStatic
        fun clearForTesting() {
            synchronized(this) {
                instance = null
            }
        }
    }

    internal data class OpChannel(
        internal val operationChannel: Channel<DatabaseOperation> = Channel(),
        internal val closeChannel: Channel<kotlin.Unit> = Channel(),
    )
}
