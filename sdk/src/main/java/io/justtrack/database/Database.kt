package io.justtrack.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.VisibleForTesting
import io.justtrack.AppVersionUpdateInfo
import io.justtrack.ApplicationVersion
import io.justtrack.AttributionDAO
import io.justtrack.AttributionDAOImpl
import io.justtrack.AttributionOutput
import io.justtrack.AttributionResponse
import io.justtrack.AttributionTimestamps
import io.justtrack.BuildConfig
import io.justtrack.LogMessageEntity
import io.justtrack.LogMetricEntity
import io.justtrack.UserEventEntity
import io.justtrack.log.Logger

internal open class Database @VisibleForTesting internal constructor(
    context: Context,
    consoleLogger: Logger,
    injectedAttributionDAO: AttributionDAO? = null,
    version: Int = 8,
    private val isDebugModeEnabled: Boolean = BuildConfig.DEBUG,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, version, CustomDatabaseErrorHandler(consoleLogger)) {

    var logger: Logger = consoleLogger

    internal constructor(
        context: Context,
        consoleLogger: Logger,
    ) : this(context, consoleLogger, null)

    @VisibleForTesting
    internal val attributionDAO: AttributionDAO = injectedAttributionDAO ?: AttributionDAOImpl(context, logger)

    init {
        // This method will suspend until onCreate or onUpgrade is completed first.
        attributionDAO.migrateFromStore(this.writableDatabase)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.let {
            createTableMessage(it)
            createTableMetric(it)
            createTableEvent(it)
            createTableEventSequenceCounter(it)

            if (!isTableExist(db, ATTRIBUTION_TABLE_NAME)) {
                attributionDAO.createTable(it)
            }
        }
    }

    @Suppress("MagicNumber") // DB version migration logic uses version numbers directly
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 3 && newVersion == 4) {
            db?.let {
                attributionDAO.createTable(it)
            }
            return
        } else if (oldVersion in 4..6) {
            db?.let {
                if (newVersion >= 8) {
                    attributionDAO.migrateAttributionToV8(it)
                } else {
                    // remove recruiter column from attribution table (4 -> 5)
                    // remove testGroupId, sdkConfig from attribution table (6 -> 7)
                    attributionDAO.dropFieldOperation(it)
                }
            }
        } else if (oldVersion == 7) {
            // Invalidate cached v4 attribution. Legacy campaign_id is a numeric ID and must never
            // become v4 campaign.externalId. Preserve identity and independent persisted state.
            db?.let {
                attributionDAO.migrateAttributionToV8(it)
            }
        }

        if (oldVersion <= 4) {
            // dropping all event pre-5.0.0
            db?.execSQL("DROP TABLE IF EXISTS $EVENT_TABLE_NAME")
        } else if (oldVersion == 5) {
            // add version column to 5.0.0
            db?.execSQL("ALTER TABLE $EVENT_TABLE_NAME ADD COLUMN $EVENT_SDK_VERSION_MAJOR LONG DEFAULT 5")
            db?.execSQL("ALTER TABLE $EVENT_TABLE_NAME ADD COLUMN $EVENT_SDK_VERSION_MINOR LONG DEFAULT 0")
            db?.execSQL("ALTER TABLE $EVENT_TABLE_NAME ADD COLUMN $EVENT_SDK_VERSION_PATCH LONG DEFAULT 0")
            db?.execSQL("ALTER TABLE $EVENT_TABLE_NAME ADD COLUMN $EVENT_SDK_VERSION_NAME Text DEFAULT '5.0.0'")
        }

        db?.execSQL("DROP TABLE IF EXISTS $MESSAGE_TABLE_NAME")
        db?.execSQL("DROP TABLE IF EXISTS $METRIC_TABLE_NAME")

        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (isDebugModeEnabled) {
            error(
                "Downgrading the database in debug mode will cause a crash. " +
                    "Please confirm this is what you want before proceeding in a production environment.",
            )
        }

        clearDatabase(db)
        onCreate(db)
    }

    internal fun setLogger(logger: Logger) {
        this.logger = logger
        this.attributionDAO.setLogger(logger)
    }

    private fun isTableExist(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    private fun createTableMessage(db: SQLiteDatabase) {
        db.execSQL(
            "create table IF NOT EXISTS $MESSAGE_TABLE_NAME (" +
                "$COMMON_ID Integer primary key AUTOINCREMENT, " +
                "$MESSAGE_LEVEL text, " +
                "$MESSAGE_MESSAGE text, " +
                "$MESSAGE_FIELDS text, " +
                "$COMMON_TIMESTAMP text, " +
                "$COMMON_TIMESTAMP_IN_MS Integer, " +
                "$COMMON_PROCESSING_TIMESTAMP_IN_MS Integer" +
                ")",
        )
    }

    private fun createTableMetric(db: SQLiteDatabase) {
        db.execSQL(
            "create table IF NOT EXISTS $METRIC_TABLE_NAME (" +
                "$COMMON_ID Integer primary key AUTOINCREMENT, " +
                "$METRIC_NAME text, " +
                "$METRIC_VALUE text, " +
                "$METRIC_DIMENSIONS text, " +
                "$METRIC_UNIT text, " +
                "$COMMON_TIMESTAMP text, " +
                "$COMMON_TIMESTAMP_IN_MS Integer, " +
                "$COMMON_PROCESSING_TIMESTAMP_IN_MS Integer" +
                ")",
        )
    }

    private fun createTableEvent(db: SQLiteDatabase) {
        db.execSQL(
            "create table IF NOT EXISTS $EVENT_TABLE_NAME (" +
                "$COMMON_ID Integer primary key AUTOINCREMENT, " +
                "$EVENT_EVENT_ID text, " +
                "$EVENT_NAME text, " +
                "$EVENT_DIMENSIONS Integer, " +
                "$EVENT_VALUE text, " +
                "$EVENT_UNIT text, " +
                "$EVENT_CURRENCY text, " +
                "$EVENT_SESSION_ID text," +
                "$COMMON_TIMESTAMP Integer, " +
                "$COMMON_TIMESTAMP_IN_MS Integer, " +
                "$COMMON_PROCESSING_TIMESTAMP_IN_MS Integer, " +
                "$EVENT_SEQUENCE_NUMBER Integer, " +
                "$EVENT_SDK_VERSION_MAJOR Integer, " +
                "$EVENT_SDK_VERSION_MINOR Integer, " +
                "$EVENT_SDK_VERSION_PATCH Integer, " +
                "$EVENT_SDK_VERSION_NAME text" +
                ")",
        )
    }

    private fun createTableEventSequenceCounter(db: SQLiteDatabase) {
        db.execSQL(
            "create table IF NOT EXISTS $EVENT_ORDERING_COUNTER_TABLE_NAME (" +
                "$COMMON_ID Integer primary key, " +
                "$SEQUENCE_COUNTER_NUMBER Long" +
                ")",
        )
    }

    internal fun insertMessage(message: LogMessageEntity): Long? = insert(MESSAGE_TABLE_NAME, message.toContentValues())

    internal fun insertMetric(metric: LogMetricEntity): Long? = insert(METRIC_TABLE_NAME, metric.toContentValues())

    internal fun insertEvent(event: UserEventEntity): Pair<Long, Long>? {
        val db = writableDatabase
        var updatedSequenceNumber: Long? = null
        var eventId: Long? = null
        try {
            db.beginTransaction()

            // fetch current sequence number
            val currentStoredSequenceNumber = getCurrentSequenceNumber(db)
            updatedSequenceNumber = if (currentStoredSequenceNumber != null) {
                currentStoredSequenceNumber + 1
            } else {
                0
            }

            // update sequence number table
            if (!updateSequenceNumber(db, updatedSequenceNumber)) {
                return null
            }

            // store event with sequence number
            eventId = db.replace(EVENT_TABLE_NAME, null, event.copy(sequenceNumber = updatedSequenceNumber).toContentValues())
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            logger.warn("Unable to insert to $EVENT_TABLE_NAME", e)
        } finally {
            closeTransaction(db)
        }

        return if (eventId != null && updatedSequenceNumber != null) {
            Pair(eventId, updatedSequenceNumber)
        } else {
            null
        }
    }

    private fun getCurrentSequenceNumber(db: SQLiteDatabase): Long? {
        var result: Long? = null
        try {
            db.rawQuery(
                "SELECT $SEQUENCE_COUNTER_NUMBER " +
                    "FROM $EVENT_ORDERING_COUNTER_TABLE_NAME " +
                    "WHERE ID = 1",
                null,
            ).use {
                if (it.moveToFirst()) {
                    result = it.getLong(it.getColumnIndexOrThrow(SEQUENCE_COUNTER_NUMBER))
                }
            }
        } catch (e: java.lang.Exception) {
            logger.warn("Unable to get event sequence number", e)
        }

        return result
    }

    private fun updateSequenceNumber(db: SQLiteDatabase, newValue: Long): Boolean {
        val entity = ContentValues().apply {
            put(COMMON_ID, 1)
            put(SEQUENCE_COUNTER_NUMBER, newValue)
        }

        try {
            db.insertWithOnConflict(EVENT_ORDERING_COUNTER_TABLE_NAME, null, entity, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            logger.warn("Unable to increment event sequence number", e)
            return false
        }
        return true
    }

    private fun insert(tableName: String, entity: ContentValues): Long? {
        val db = writableDatabase

        var resultId: Long? = null
        try {
            db.beginTransaction()
            resultId = db.replace(tableName, null, entity)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            logger.warn("Unable to insert to $tableName", e)
        } finally {
            closeTransaction(db)
        }

        return resultId
    }

    internal fun getNextBatchAndMarkTransactionMessage(batchSize: Int): List<LogMessageEntity> = getNextBatchAndMarkTransaction(
        MESSAGE_TABLE_NAME,
        batchSize,
        { cursor ->
            transformMessage(cursor)
        },
    ) { markTime, entity ->
        entity.copy(processingTimeInMS = markTime)
    }

    internal fun getNextBatchAndMarkTransactionMetric(batchSize: Int) = getNextBatchAndMarkTransaction(
        METRIC_TABLE_NAME,
        batchSize,
        { cursor ->
            transformMetric(cursor)
        },
    ) { markTime, entity ->
        entity.copy(processingTimeInMS = markTime)
    }

    internal fun getNextBatchAndMarkTransactionEvent(batchSize: Int): List<UserEventEntity> = getNextBatchAndMarkTransaction(
        EVENT_TABLE_NAME,
        batchSize,
        { cursor ->
            transformEvent(cursor)
        },
    ) { markTime, entity ->
        entity.copy(processingTimeInMS = markTime)
    }

    private fun <T : DatabaseEntity> getNextBatchAndMarkTransaction(
        tableName: String,
        batchSize: Int,
        transformFunction: (cursor: Cursor) -> List<T>,
        setProcessingTime: (markTime: Long, elem: T) -> T,
    ): List<T> {
        val db = writableDatabase
        var batch = listOf<T>()
        try {
            db.beginTransaction()
            val markTime = System.currentTimeMillis()
            batch = getNextBatch(tableName, batchSize, transformFunction).map {
                setProcessingTime(markTime, it)
            }
            mark(tableName, batch.map { it.id }, markTime)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            logger.warn("Unable to getNextBatchAndMarkTransaction for table $tableName", e)
        } finally {
            closeTransaction(db)
        }

        return batch
    }

    private fun <T> getNextBatch(tableName: String, batchSize: Int, transformFunction: (cursor: Cursor) -> List<T>): List<T> {
        val db = readableDatabase
        val dataList = ArrayList<T>()
        val cutoffMillis = System.currentTimeMillis() - PROCESSING_TIMEOUT_MILLIS
        try {
            db.rawQuery(
                "SELECT * " +
                    "FROM ($tableName) " +
                    "WHERE ($COMMON_PROCESSING_TIMESTAMP_IN_MS) < $cutoffMillis " +
                    "ORDER BY ($COMMON_ID) ASC LIMIT ($batchSize)",
                null,
            ).use {
                dataList.addAll(transformFunction(it))
            }
        } catch (e: java.lang.Exception) {
            logger.warn("Unable to getNextBatch", e)
        }
        return dataList
    }

    internal fun deleteById(tableName: String, idList: List<Long>): Boolean {
        val db = writableDatabase
        val idsString = idList.joinToString(",") // convert list to comma-separated string
        try {
            db.beginTransaction()
            db.delete(tableName, "($COMMON_ID) IN ($idsString)", null)
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            logger.warn("Unable to deleteById", e)
        } finally {
            closeTransaction(db)
        }
        return false
    }

    internal fun deleteByDate(tableName: String, cutoffMS: Long): Boolean {
        val db = writableDatabase
        try {
            db.beginTransaction()
            db.delete(tableName, "($COMMON_TIMESTAMP_IN_MS) <= ($cutoffMS)", null)
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            logger.warn("Unable to deleteByDate", e)
        } finally {
            closeTransaction(db)
        }
        return false
    }

    internal fun unMark(tableName: String, idList: List<Long>): Boolean {
        val db = writableDatabase
        val contentValues = ContentValues().apply {
            put(COMMON_PROCESSING_TIMESTAMP_IN_MS, -1)
        }
        val idsString = idList.joinToString(",") // convert list to comma-separated string
        try {
            db.beginTransaction()
            db.update(
                tableName,
                contentValues,
                "($COMMON_PROCESSING_TIMESTAMP_IN_MS) != -1 AND ($COMMON_ID) IN ($idsString)",
                null,
            )
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            logger.warn("Unable to unMarkFailed", e)
        } finally {
            closeTransaction(db)
        }
        return false
    }

    internal fun mark(tableName: String, idList: List<Long>, markTime: Long): Boolean {
        val db = writableDatabase
        val contentValues = ContentValues().apply {
            put(COMMON_PROCESSING_TIMESTAMP_IN_MS, markTime)
        }
        val idsString = idList.joinToString(",") // convert list to comma-separated string
        try {
            db.beginTransaction()
            db.update(
                tableName,
                contentValues,
                "($COMMON_ID) IN ($idsString)",
                null,
            )
            db.setTransactionSuccessful()
            return true
        } catch (e: Exception) {
            logger.warn("Unable to mark", e)
        } finally {
            closeTransaction(db)
        }
        return false
    }

    internal fun <T> getAll(tableName: String, transformFunction: (cursor: Cursor) -> List<T>): List<T> {
        val db = readableDatabase
        val dataList = ArrayList<T>()
        try {
            db.rawQuery(
                "SELECT * FROM $tableName ORDER BY $COMMON_TIMESTAMP_IN_MS ASC",
                null,
            ).use {
                dataList.addAll(transformFunction(it))
            }
        } catch (e: java.lang.Exception) {
            logger.warn("Unable to getAll", e)
        }

        return dataList
    }

    internal fun <T> getAllUnMark(tableName: String, transformFunction: (cursor: Cursor) -> List<T>): List<T> {
        val db = readableDatabase
        val dataList = ArrayList<T>()
        val cutoffMillis = System.currentTimeMillis() - PROCESSING_TIMEOUT_MILLIS
        try {
            db.rawQuery(
                "SELECT * " +
                    "FROM ($tableName) " +
                    "WHERE ($COMMON_PROCESSING_TIMESTAMP_IN_MS) < $cutoffMillis " +
                    "ORDER BY ($COMMON_TIMESTAMP_IN_MS) ASC",
                null,
            ).use {
                dataList.addAll(transformFunction(it))
            }
        } catch (e: java.lang.Exception) {
            logger.warn("Unable to getAllUnMark", e)
        }
        return dataList
    }

    internal fun nukeTableMessage() = nukeTable(MESSAGE_TABLE_NAME)

    internal fun nukeTableMetric() = nukeTable(METRIC_TABLE_NAME)

    internal fun nukeTableEvent() = nukeTable(EVENT_TABLE_NAME)

    private fun nukeTable(tableName: String) {
        val db = writableDatabase
        try {
            db.beginTransaction()
            db.execSQL("DELETE FROM $tableName")
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            logger.warn("Unable to nuke table $tableName", e)
        } finally {
            closeTransaction(db)
        }
    }

    internal fun setIntegrityTokenSent(newValue: Boolean): Boolean {
        val db = this.writableDatabase

        val contentValues = ContentValues().apply {
            put(ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT, newValue)
        }
        try {
            db.beginTransaction()
            db.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            db.setTransactionSuccessful()
            return true
        } catch (exception: Exception) {
            logger.warn("Unable to set integrity token sent", exception)
        } finally {
            closeTransaction(db)
        }
        return false
    }

    internal fun isIntegrityTokenSent(): Boolean {
        val db = readableDatabase
        try {
            db.query(
                ATTRIBUTION_TABLE_NAME,
                arrayOf(ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT),
                null,
                null,
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = if (cursor.isNull(cursor.getColumnIndexOrThrow(ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT))) {
                        null
                    } else {
                        cursor.getInt(cursor.getColumnIndexOrThrow(ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT)) != 0
                    }
                    return result ?: false
                } else {
                    error("Database cursor can not move to first position")
                }
            }
        } catch (e: java.lang.Exception) {
            logger.warn("Unable to retrieve isIntegrityTokenSent", e)
        }

        return false
    }

    internal fun setIntegritySecret(secretValue: String): Boolean {
        val db = this.writableDatabase

        val contentValues = ContentValues().apply {
            put(ATTRIBUTION_INTEGRITY_SECRET, secretValue)
        }
        try {
            db.beginTransaction()
            db.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            db.setTransactionSuccessful()
            return true
        } catch (exception: Exception) {
            logger.warn("Unable to set integrity secret", exception)
        } finally {
            closeTransaction(db)
        }
        return false
    }

    internal fun getIntegritySecret(): String? {
        val db = readableDatabase
        try {
            db.query(
                ATTRIBUTION_TABLE_NAME,
                arrayOf(ATTRIBUTION_INTEGRITY_SECRET),
                null,
                null,
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = if (cursor.isNull(cursor.getColumnIndexOrThrow(ATTRIBUTION_INTEGRITY_SECRET))) {
                        null
                    } else {
                        cursor.getString(cursor.getColumnIndexOrThrow(ATTRIBUTION_INTEGRITY_SECRET))
                    }
                    return result
                } else {
                    error("Database cursor can not move to first position")
                }
            }
        } catch (e: java.lang.Exception) {
            logger.warn("Database Unable to retrieve integrity token", e)
        }

        return null
    }

    internal fun setAttributionFinished(response: AttributionResponse) {
        attributionDAO.setAttributionFinished(this.writableDatabase, response)
    }

    internal fun getStoredOutput(): AttributionOutput? {
        return attributionDAO.getStoredOutput(this.readableDatabase)
    }

    internal fun getAttributionTimestamps(): AttributionTimestamps? {
        return attributionDAO.getAttributionTimestamps(this.readableDatabase)
    }

    internal fun setLastOpen(currentMs: Long) {
        attributionDAO.setLastOpen(this.writableDatabase, currentMs)
    }

    internal fun getAppVersionUpdateInfo(currentVersion: ApplicationVersion): AppVersionUpdateInfo {
        return attributionDAO.getAppVersionUpdateInfo(this.writableDatabase, currentVersion)
    }

    internal fun getInstallId(): String? {
        return attributionDAO.getInstallId(this.readableDatabase)
    }

    internal fun setInstallId(installId: String): Boolean {
        return attributionDAO.setInstallId(this.writableDatabase, installId)
    }

    internal fun getUserId(): String? {
        return attributionDAO.getUserId(this.readableDatabase)
    }

    internal fun setUserid(userId: String): Boolean {
        return attributionDAO.setUserId(this.writableDatabase, userId)
    }

    @VisibleForTesting
    @Synchronized
    internal fun closeTransaction(db: SQLiteDatabase) {
        if (db.isOpen) {
            try {
                db.endTransaction()
            } catch (error: Exception) {
                logger.warn("Failed to close transaction", error)
            }
        }
    }

    @Suppress("MagicNumber") // column index is based on the order of columns in the table, which is fixed
    internal fun transformMessage(cursor: Cursor): List<LogMessageEntity> {
        val result = mutableListOf<LogMessageEntity>()
        while (cursor.moveToNext()) {
            result.add(
                LogMessageEntity(
                    id = cursor.getLong(0),
                    level = cursor.getString(1),
                    message = cursor.getString(2),
                    fields = cursor.getString(3),
                    timestamp = cursor.getString(4),
                    timestampInMS = cursor.getLong(5),
                    processingTimeInMS = cursor.getLong(6),
                ),
            )
        }
        return result
    }

    @Suppress("MagicNumber") // column index is based on the order of columns in the table, which is fixed
    internal fun transformMetric(cursor: Cursor): List<LogMetricEntity> {
        val result = mutableListOf<LogMetricEntity>()
        while (cursor.moveToNext()) {
            result.add(
                LogMetricEntity(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    value = cursor.getDouble(2),
                    dimensions = cursor.getString(3),
                    unit = cursor.getString(4),
                    timestamp = cursor.getString(5),
                    timestampInMS = cursor.getLong(6),
                    processingTimeInMS = cursor.getLong(7),
                ),
            )
        }
        return result
    }

    @Suppress("MagicNumber") // column index is based on the order of columns in the table, which is fixed
    internal fun transformEvent(cursor: Cursor): List<UserEventEntity> {
        val result = mutableListOf<UserEventEntity>()
        while (cursor.moveToNext()) {
            result.add(
                UserEventEntity(
                    id = cursor.getLong(0),
                    eventId = cursor.getString(1),
                    eventName = cursor.getString(2),
                    dimensions = cursor.getString(3),
                    value = cursor.getDouble(4),
                    unit = cursor.getString(5),
                    currency = cursor.getString(6),
                    sessionId = cursor.getString(7),
                    timestamp = cursor.getString(8),
                    timestampInMS = cursor.getLong(9),
                    processingTimeInMS = cursor.getLong(10),
                    sequenceNumber = cursor.getLong(11),
                    sdkVersionMajor = cursor.getLong(12),
                    sdkVersionMinor = cursor.getLong(13),
                    sdkVersionPatch = cursor.getLong(14),
                    sdkVersionName = cursor.getString(15),
                ),
            )
        }
        return result
    }

    private fun clearDatabase(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF;")
        db.beginTransaction()
        try {
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';",
                null,
            )

            while (cursor.moveToNext()) {
                val tableName = cursor.getString(0)
                db.execSQL("DROP TABLE IF EXISTS $tableName")
            }
            cursor.close()

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys=ON;")
        }
    }

    internal companion object {
        internal const val DATABASE_NAME = "justtrack"

        internal const val COMMON_ID = "id"
        internal const val COMMON_TIMESTAMP = "timestamp"
        internal const val COMMON_TIMESTAMP_IN_MS = "timestampInMS"
        internal const val COMMON_PROCESSING_TIMESTAMP_IN_MS = "processingTimeInMS"

        internal const val MESSAGE_TABLE_NAME = "message"
        internal const val MESSAGE_LEVEL = "level"
        internal const val MESSAGE_MESSAGE = "message"
        internal const val MESSAGE_FIELDS = "fields"

        internal const val METRIC_TABLE_NAME = "metric"
        internal const val METRIC_NAME = "name"
        internal const val METRIC_VALUE = "value"
        internal const val METRIC_DIMENSIONS = "dimensions"
        internal const val METRIC_UNIT = "unit"

        internal const val EVENT_TABLE_NAME = "event"
        internal const val EVENT_EVENT_ID = "eventId"
        internal const val EVENT_NAME = "eventName"
        internal const val EVENT_DIMENSIONS = "dimensions"
        internal const val EVENT_VALUE = "value"
        internal const val EVENT_UNIT = "unit"
        internal const val EVENT_CURRENCY = "currency"
        internal const val EVENT_SESSION_ID = "sessionId"
        internal const val EVENT_SDK_VERSION_MAJOR = "sdkVersionMajor"
        internal const val EVENT_SDK_VERSION_MINOR = "sdkVersionMinor"
        internal const val EVENT_SDK_VERSION_PATCH = "sdkVersionPatch"
        internal const val EVENT_SDK_VERSION_NAME = "sdkVersionName"
        internal const val EVENT_SEQUENCE_NUMBER = "sequenceNumber"

        internal const val EVENT_ORDERING_COUNTER_TABLE_NAME = "eventSequenceCounter"
        internal const val SEQUENCE_COUNTER_NUMBER = "sequenceCounterNumber"

        internal const val ATTRIBUTION_TABLE_NAME = "attributionTable"
        internal const val ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT = "isIntegrityTokenSent"
        internal const val ATTRIBUTION_INTEGRITY_SECRET = "integritySecret"

        // we retry sending data 5 times, each time can take up to 2 minutes before it fails
        // (30s connect timeout, 30s write timeout, 30s read timeout, 15s max delay between the retries)
        // thus, we mark events as ready for retry automatically after 10 minutes
        internal const val PROCESSING_TIMEOUT_MILLIS = 5 * 2 * 60 * 1000

        @VisibleForTesting
        @JvmStatic
        @JvmName("clearForTesting")
        internal fun clearForTesting(context: Context) {
            context.deleteDatabase(DATABASE_NAME)
        }
    }
}

internal interface DatabaseEntity {
    val id: Long

    fun toContentValues(): ContentValues
}
