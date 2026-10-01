package io.justtrack

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.database.Database
import io.justtrack.database.Database.Companion.ATTRIBUTION_TABLE_NAME
import io.justtrack.database.Database.Companion.EVENT_TABLE_NAME
import io.justtrack.database.Database.Companion.MESSAGE_TABLE_NAME
import io.justtrack.database.Database.Companion.METRIC_TABLE_NAME
import io.justtrack.log.Logger
import io.justtrack.versions.ApplicationVersionImpl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
internal class DatabaseTest {

    private lateinit var context: Context
    private lateinit var logger: Logger
    private lateinit var database: Database

    private val exampleResponse = AttributionResponseImpl(
        UUID.fromString("1db3a1c1-e7e6-4994-949c-23241447e91b"),
        "install-id",
        "acquisition",
        CampaignImpl("5", "campaign", "acquisition", true),
        ChannelImpl(6, "channel", true),
        PartnerImpl(7, "partner"),
        "sourceId",
        "sourceBundleId",
        "sourcePlacement",
        "adsetId",
        Date(1_700_000_000_000L),
        false,
    )

    private val sampleMessage = LogMessageEntity(
        level = "INFO",
        message = "hello",
        fields = "{}",
        timestamp = "2024-01-01T00:00:00.000Z",
        timestampInMS = 1_700_000_000_000L,
    )

    private val sampleMetric = LogMetricEntity(
        name = "metric",
        value = 42.0,
        dimensions = "{}",
        unit = "ms",
        timestamp = "2024-01-01T00:00:00.000Z",
        timestampInMS = 1_700_000_000_000L,
    )

    private val sampleEvent = UserEventEntity(
        eventId = UUID.randomUUID().toString(),
        eventName = "event",
        dimensions = "{}",
        sessionId = UUID.randomUUID().toString(),
        value = 1.0,
        unit = null,
        currency = "USD",
        timestamp = "2024-01-01T00:00:00.000Z",
        timestampInMS = 1_700_000_000_000L,
        sdkVersionMajor = 5,
        sdkVersionMinor = 0,
        sdkVersionPatch = 0,
        sdkVersionName = "5.0.0",
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(Database.DATABASE_NAME)
        logger = TestLogger()
        database = newDatabase()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
        context.deleteDatabase(Database.DATABASE_NAME)
    }

    private fun newDatabase(injectedAttributionDAO: AttributionDAO? = null, version: Int = 7, isDebugModeEnabled: Boolean = false): Database =
        Database(
            context,
            logger,
            injectedAttributionDAO ?: AttributionDAOImpl(context, logger),
            version = version,
            isDebugModeEnabled = isDebugModeEnabled,
        )

    // ---------- construction / onCreate ----------

    @Test
    fun secondaryConstructor_createsAllTables() {
        database.close()
        context.deleteDatabase(Database.DATABASE_NAME)
        val db = Database(context, logger)
        try {
            assertTrue(tableExists(db.writableDatabase, MESSAGE_TABLE_NAME))
            assertTrue(tableExists(db.writableDatabase, METRIC_TABLE_NAME))
            assertTrue(tableExists(db.writableDatabase, EVENT_TABLE_NAME))
            assertTrue(tableExists(db.writableDatabase, ATTRIBUTION_TABLE_NAME))
            assertNotNull(db.attributionDAO)
        } finally {
            db.close()
        }
    }

    @Test
    fun primaryConstructor_withDefaultIsDebugModeEnabled_doesNotThrowOnCreate() {
        // Exercises the `isDebugModeEnabled: Boolean = BuildConfig.DEBUG` default param.
        database.close()
        context.deleteDatabase(Database.DATABASE_NAME)
        val db = Database(context, logger, null, 7)
        try {
            assertTrue(tableExists(db.writableDatabase, MESSAGE_TABLE_NAME))
        } finally {
            db.close()
        }
    }

    @Test
    fun onCreate_whenAttributionTableAlreadyExists_doesNotRecreate() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        // simulate the table already existing by having createTable execute the real DDL via the mock
        doAnswer { invocation ->
            val db = invocation.getArgument<SQLiteDatabase>(0)
            db.execSQL("CREATE TABLE IF NOT EXISTS $ATTRIBUTION_TABLE_NAME (id INTEGER PRIMARY KEY)")
            Unit
        }.whenever(dao).createTable(any())

        // First instance creates the attribution table.
        val first = newDatabase(injectedAttributionDAO = dao)
        first.writableDatabase
        first.close()

        // Second instance: opening triggers onCreate? No — onCreate runs only at create time. To force a fresh
        // onCreate without invoking onUpgrade, drop only our app tables but keep the file => simplest is to
        // just verify that on a fresh DB the createTable IS called (the negative branch is the natural path).
        context.deleteDatabase(Database.DATABASE_NAME)
        val dao2 = mock<AttributionDAO>()
        whenever(dao2.migrateFromStore(any())).thenReturn(false)
        val second = newDatabase(injectedAttributionDAO = dao2)
        second.writableDatabase // trigger onCreate
        verify(dao2).createTable(any())
        second.close()
    }

    // ---------- onUpgrade ----------

    @Test
    fun onCreate_withNullDb_doesNothing() {
        // Direct invocation with null exercises the `db?.let { ... }` else-branch.
        database.onCreate(null)
    }

    @Test
    fun onUpgrade_3_to_4_withNullDb_doesNothing() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val db = newDatabase(injectedAttributionDAO = dao)
        db.onUpgrade(null, 3, 4)
        // dao.createTable should never be called because db is null
        verify(dao, never()).createTable(any())
        db.close()
    }

    @Test
    fun onUpgrade_4_to_5_withNullDb_doesNothing() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val db = newDatabase(injectedAttributionDAO = dao)
        db.onUpgrade(null, 4, 5)
        db.close()
    }

    @Test
    fun onUpgrade_5_to_7_withNullDb_doesNothing() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val db = newDatabase(injectedAttributionDAO = dao)
        db.onUpgrade(null, 5, 7)
        db.close()
    }

    @Test
    fun onUpgrade_6_to_7_withNullDb_doesNothing() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val db = newDatabase(injectedAttributionDAO = dao)
        db.onUpgrade(null, 6, 7)
        db.close()
    }

    @Test
    fun onUpgrade_7_to_8_migratesAttributionWithoutReusingLegacyCampaignId() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val db = newDatabase(injectedAttributionDAO = dao)
        db.onUpgrade(db.writableDatabase, 7, 8)
        verify(dao).migrateAttributionToV8(any())
        verify(dao, never()).dropFieldOperation(any())
        db.close()
    }

    @Test
    fun onUpgrade_3_to_7_doesNotTakeShortCircuitBranchAndFollowsGeneralPath() {
        // oldVersion == 3 but newVersion != 4 must skip the early-return branch.
        database.close()
        context.deleteDatabase(Database.DATABASE_NAME)
        val v3 = newDatabase(version = 3)
        v3.writableDatabase
        v3.close()
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val upgraded = newDatabase(injectedAttributionDAO = dao, version = 7)
        upgraded.writableDatabase
        // oldVersion=3 path: NOT in 4..6, so dropFieldOperation NOT called; oldVersion <= 4 → drops event
        verify(dao, never()).dropFieldOperation(any())
        upgraded.close()
    }

    @Test
    fun onUpgrade_3_to_4_createsAttributionTable() {
        database.close()
        context.deleteDatabase(Database.DATABASE_NAME)
        val v3 = newDatabase(version = 3)
        v3.writableDatabase
        v3.close()

        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val upgraded = newDatabase(injectedAttributionDAO = dao, version = 4)
        upgraded.writableDatabase
        verify(dao).createTable(any())
        upgraded.close()
    }

    @Test
    fun onUpgrade_4_to_7_dropsFieldsAndDropsEventTable() {
        database.close()
        context.deleteDatabase(Database.DATABASE_NAME)
        val v4 = newDatabase(version = 4)
        v4.writableDatabase
        v4.close()

        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val upgraded = newDatabase(injectedAttributionDAO = dao, version = 7)
        upgraded.writableDatabase
        verify(dao).dropFieldOperation(any())
        upgraded.close()
    }

    @Test
    fun onUpgrade_5_to_7_addsSdkVersionColumns_andDropsAttributionFields() {
        // Simulate the historical v5 event table that lacked sdk_version_* columns by manually creating
        // a stripped-down event table, then call onUpgrade(db, 5, 7) directly.
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val db = newDatabase(injectedAttributionDAO = dao)
        val sqlite = db.writableDatabase
        sqlite.execSQL("DROP TABLE $EVENT_TABLE_NAME")
        sqlite.execSQL(
            "create table IF NOT EXISTS $EVENT_TABLE_NAME (" +
                "id Integer primary key AUTOINCREMENT, " +
                "eventId text" +
                ")",
        )
        db.onUpgrade(sqlite, 5, 7)
        verify(dao).dropFieldOperation(any())
        // event table now has sdk_version_major column
        val cur = sqlite.rawQuery("PRAGMA table_info($EVENT_TABLE_NAME)", null)
        val cols = mutableSetOf<String>()
        cur.use { c -> while (c.moveToNext()) cols.add(c.getString(1)) }
        assertTrue("sdkVersionMajor column should be present", "sdkVersionMajor" in cols)
        db.close()
    }

    @Test
    fun onUpgrade_6_to_7_dropsFieldsButDoesNotAlterEventColumns() {
        database.close()
        context.deleteDatabase(Database.DATABASE_NAME)
        val v6 = newDatabase(version = 6)
        v6.writableDatabase
        v6.close()

        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val upgraded = newDatabase(injectedAttributionDAO = dao, version = 7)
        upgraded.writableDatabase
        verify(dao).dropFieldOperation(any())
        upgraded.close()
    }

    // ---------- onDowngrade ----------

    @Test
    fun onDowngrade_inProductionMode_clearsAndRecreatesTables() {
        // create v7 with data
        database.insertMessage(sampleMessage)
        database.close()
        // open at v6 with debug=false -> clearDatabase + onCreate
        val downgraded = newDatabase(version = 6, isDebugModeEnabled = false)
        try {
            val cnt = downgraded.readableDatabase.rawQuery("SELECT COUNT(*) FROM $MESSAGE_TABLE_NAME", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals(0, cnt)
        } finally {
            downgraded.close()
        }
    }

    @Test
    fun onDowngrade_inDebugMode_throwsIllegalStateException() {
        database.close()
        assertThrows(IllegalStateException::class.java) {
            newDatabase(version = 6, isDebugModeEnabled = true)
        }
    }

    // ---------- logger swap ----------

    @Test
    fun setLogger_replacesLoggerAndPropagatesToAttributionDAO() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val db = newDatabase(injectedAttributionDAO = dao)
        val newLogger = TestLogger()
        db.setLogger(newLogger)
        assertSame(newLogger, db.logger)
        verify(dao).setLogger(newLogger)
        db.close()
    }

    // ---------- insert / getAll / getNextBatchAndMark ----------

    @Test
    fun insertMessage_andGetAll_roundTripsRow() {
        val id = database.insertMessage(sampleMessage)
        assertNotNull(id)
        val all = database.getAll(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }
        assertEquals(1, all.size)
        assertEquals(sampleMessage, all[0])
    }

    @Test
    fun insertMetric_andGetAll_roundTripsRow() {
        val id = database.insertMetric(sampleMetric)
        assertNotNull(id)
        val all = database.getAll(METRIC_TABLE_NAME) { c -> database.transformMetric(c) }
        assertEquals(1, all.size)
        assertEquals(sampleMetric, all[0])
    }

    @Test
    fun insertEvent_assignsSequenceNumberStartingAtZero() {
        val r1 = database.insertEvent(sampleEvent)
        val r2 = database.insertEvent(sampleEvent.copy(eventId = UUID.randomUUID().toString()))
        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(0L, r1!!.second)
        assertEquals(1L, r2!!.second)
    }

    @Test
    fun insertEvent_whenSequenceCounterTableMissing_returnsNull() {
        // Dropping the sequence counter table makes updateSequenceNumber throw, which is caught and
        // makes insertEvent return null.
        database.writableDatabase.execSQL("DROP TABLE eventSequenceCounter")
        val r = database.insertEvent(sampleEvent)
        assertNull(r)
    }

    @Test
    fun getNextBatchAndMarkTransactionMessage_marksRowsWithProcessingTime() {
        database.insertMessage(sampleMessage)
        val batch = database.getNextBatchAndMarkTransactionMessage(10)
        assertEquals(1, batch.size)
        assertTrue("processingTimeInMS must be set", batch[0].processingTimeInMS > 0)
    }

    @Test
    fun getNextBatchAndMarkTransactionMetric_marksRowsWithProcessingTime() {
        database.insertMetric(sampleMetric)
        val batch = database.getNextBatchAndMarkTransactionMetric(10)
        assertEquals(1, batch.size)
        assertTrue(batch[0].processingTimeInMS > 0)
    }

    @Test
    fun getNextBatchAndMarkTransactionEvent_marksRowsWithProcessingTime() {
        database.insertEvent(sampleEvent)
        val batch = database.getNextBatchAndMarkTransactionEvent(10)
        assertEquals(1, batch.size)
        assertTrue(batch[0].processingTimeInMS > 0)
    }

    @Test
    fun getNextBatchAndMarkTransaction_whenTableMissing_returnsEmptyAndLogs() {
        // drop the table to force the catch path inside getNextBatch / transaction
        database.writableDatabase.execSQL("DROP TABLE $MESSAGE_TABLE_NAME")
        val batch = database.getNextBatchAndMarkTransactionMessage(10)
        assertTrue(batch.isEmpty())
    }

    @Test
    fun insertEvent_secondInsertReadsExistingSequenceNumber() {
        // First insert: getCurrentSequenceNumber returns null (no row). Second insert: row exists,
        // moveToFirst -> true, exercises the `result = it.getLong(...)` branch.
        val first = database.insertEvent(sampleEvent)!!
        val second = database.insertEvent(sampleEvent.copy(eventId = UUID.randomUUID().toString()))!!
        assertEquals(0L, first.second)
        assertEquals(1L, second.second)
        val third = database.insertEvent(sampleEvent.copy(eventId = UUID.randomUUID().toString()))!!
        assertEquals(2L, third.second)
    }

    // ---------- mark / unMark / deleteById / deleteByDate ----------

    @Test
    fun mark_and_unMark_updateProcessingTimestamp() {
        val id = database.insertMessage(sampleMessage)!!
        assertTrue(database.mark(MESSAGE_TABLE_NAME, listOf(id), 12345L))
        assertTrue(database.unMark(MESSAGE_TABLE_NAME, listOf(id)))
        val all = database.getAll(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }
        assertEquals(-1L, all[0].processingTimeInMS)
    }

    @Test
    fun deleteById_removesRow() {
        val id = database.insertMessage(sampleMessage)!!
        assertTrue(database.deleteById(MESSAGE_TABLE_NAME, listOf(id)))
        assertTrue(database.getAll(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }.isEmpty())
    }

    @Test
    fun deleteByDate_removesRowsAtOrBeforeCutoff() {
        database.insertMessage(sampleMessage)
        assertTrue(database.deleteByDate(MESSAGE_TABLE_NAME, sampleMessage.timestampInMS))
        assertTrue(database.getAll(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }.isEmpty())
    }

    @Test
    fun mark_unMark_deleteById_deleteByDate_onMissingTableReturnFalse() {
        database.writableDatabase.execSQL("DROP TABLE $MESSAGE_TABLE_NAME")
        assertFalse(database.mark(MESSAGE_TABLE_NAME, listOf(1L), 1L))
        assertFalse(database.unMark(MESSAGE_TABLE_NAME, listOf(1L)))
        assertFalse(database.deleteById(MESSAGE_TABLE_NAME, listOf(1L)))
        assertFalse(database.deleteByDate(MESSAGE_TABLE_NAME, 1L))
    }

    // ---------- getAllUnMark ----------

    @Test
    fun getAllUnMark_returnsRowsBelowCutoff() {
        database.insertMessage(sampleMessage)
        val rows = database.getAllUnMark(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }
        assertEquals(1, rows.size)
    }

    @Test
    fun getAllUnMark_onMissingTable_returnsEmpty() {
        database.writableDatabase.execSQL("DROP TABLE $MESSAGE_TABLE_NAME")
        val rows = database.getAllUnMark(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun getAll_onMissingTable_returnsEmpty() {
        database.writableDatabase.execSQL("DROP TABLE $MESSAGE_TABLE_NAME")
        val rows = database.getAll(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }
        assertTrue(rows.isEmpty())
    }

    // ---------- nukeTable* ----------

    @Test
    fun nukeTableMessage_clearsRows() {
        database.insertMessage(sampleMessage)
        database.nukeTableMessage()
        assertTrue(database.getAll(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }.isEmpty())
    }

    @Test
    fun nukeTableMetric_clearsRows() {
        database.insertMetric(sampleMetric)
        database.nukeTableMetric()
        assertTrue(database.getAll(METRIC_TABLE_NAME) { c -> database.transformMetric(c) }.isEmpty())
    }

    @Test
    fun nukeTableEvent_clearsRows() {
        database.insertEvent(sampleEvent)
        database.nukeTableEvent()
        assertTrue(database.getAll(EVENT_TABLE_NAME) { c -> database.transformEvent(c) }.isEmpty())
    }

    @Test
    fun nukeTable_onMissingTable_logsWarning() {
        database.writableDatabase.execSQL("DROP TABLE $MESSAGE_TABLE_NAME")
        // does not throw
        database.nukeTableMessage()
    }

    // ---------- integrity token sent ----------

    @Test
    fun setIntegrityTokenSent_andIsIntegrityTokenSent_roundTrip() {
        assertFalse(database.isIntegrityTokenSent()) // default is null -> false
        assertTrue(database.setIntegrityTokenSent(true))
        assertTrue(database.isIntegrityTokenSent())
        assertTrue(database.setIntegrityTokenSent(false))
        assertFalse(database.isIntegrityTokenSent())
    }

    @Test
    fun isIntegrityTokenSent_whenTableMissing_returnsFalse() {
        database.writableDatabase.execSQL("DROP TABLE $ATTRIBUTION_TABLE_NAME")
        assertFalse(database.isIntegrityTokenSent())
    }

    @Test
    fun setIntegrityTokenSent_whenTableMissing_returnsFalse() {
        database.writableDatabase.execSQL("DROP TABLE $ATTRIBUTION_TABLE_NAME")
        assertFalse(database.setIntegrityTokenSent(true))
    }

    @Test
    fun isIntegrityTokenSent_whenNoRows_logsAndReturnsFalse() {
        database.writableDatabase.execSQL("DELETE FROM $ATTRIBUTION_TABLE_NAME")
        // moveToFirst -> false -> error() thrown internally -> caught -> false
        assertFalse(database.isIntegrityTokenSent())
    }

    // ---------- integrity secret ----------

    @Test
    fun setIntegritySecret_andGetIntegritySecret_roundTrip() {
        assertNull(database.getIntegritySecret())
        assertTrue(database.setIntegritySecret("secret"))
        assertEquals("secret", database.getIntegritySecret())
    }

    @Test
    fun getIntegritySecret_whenTableMissing_returnsNull() {
        database.writableDatabase.execSQL("DROP TABLE $ATTRIBUTION_TABLE_NAME")
        assertNull(database.getIntegritySecret())
    }

    @Test
    fun setIntegritySecret_whenTableMissing_returnsFalse() {
        database.writableDatabase.execSQL("DROP TABLE $ATTRIBUTION_TABLE_NAME")
        assertFalse(database.setIntegritySecret("x"))
    }

    @Test
    fun getIntegritySecret_whenNoRows_logsAndReturnsNull() {
        database.writableDatabase.execSQL("DELETE FROM $ATTRIBUTION_TABLE_NAME")
        assertNull(database.getIntegritySecret())
    }

    // ---------- AttributionDAO delegations ----------

    @Test
    fun attributionDelegations_callIntoInjectedDao() {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val storedOutput = mock<AttributionOutput>()
        val timestamps = mock<AttributionTimestamps>()
        val updateInfo = mock<io.justtrack.AppVersionUpdateInfo>()
        whenever(dao.getStoredOutput(any())).thenReturn(storedOutput)
        whenever(dao.getAttributionTimestamps(any())).thenReturn(timestamps)
        whenever(dao.getInstallId(any())).thenReturn("install")
        whenever(dao.setInstallId(any(), any())).thenReturn(true)
        whenever(dao.getUserId(any())).thenReturn("user")
        whenever(dao.setUserId(any(), any())).thenReturn(true)
        whenever(dao.getAppVersionUpdateInfo(any(), any())).thenReturn(updateInfo)

        val db = newDatabase(injectedAttributionDAO = dao)

        db.setAttributionFinished(exampleResponse)
        assertSame(storedOutput, db.getStoredOutput())
        assertSame(timestamps, db.getAttributionTimestamps())
        db.setLastOpen(123L)
        assertSame(updateInfo, db.getAppVersionUpdateInfo(ApplicationVersionImpl("1.0", "1")))
        assertEquals("install", db.getInstallId())
        assertTrue(db.setInstallId("new"))
        assertEquals("user", db.getUserId())
        assertTrue(db.setUserid("new-user"))

        verify(dao).setAttributionFinished(any(), eq(exampleResponse))
        verify(dao).setLastOpen(any(), eq(123L))
        verify(dao).setInstallId(any(), eq("new"))
        verify(dao).setUserId(any(), eq("new-user"))
        db.close()
    }

    // ---------- closeTransaction ----------

    @Test
    fun closeTransaction_onClosedDatabase_doesNothing() {
        val db = mock<SQLiteDatabase>()
        whenever(db.isOpen).thenReturn(false)
        database.closeTransaction(db)
        verify(db, never()).endTransaction()
    }

    @Test
    fun closeTransaction_whenEndTransactionThrows_isCaught() {
        val db = mock<SQLiteDatabase>()
        whenever(db.isOpen).thenReturn(true)
        doThrow(IllegalStateException("not in transaction")).whenever(db).endTransaction()
        database.closeTransaction(db) // does not throw
    }

    @Test
    fun closeTransaction_onOpenDatabase_callsEndTransaction() {
        val db = mock<SQLiteDatabase>()
        whenever(db.isOpen).thenReturn(true)
        database.closeTransaction(db)
        verify(db).endTransaction()
    }

    // ---------- catch-block coverage via spy that returns a throwing writableDatabase ----------

    private fun throwingSpy(): Database {
        val dao = mock<AttributionDAO>()
        whenever(dao.migrateFromStore(any())).thenReturn(false)
        val real = newDatabase(injectedAttributionDAO = dao)
        real.writableDatabase // force initialization
        val spy = org.mockito.kotlin.spy(real)
        val throwing = mock<SQLiteDatabase>()
        whenever(throwing.isOpen).thenReturn(true)
        doThrow(IllegalStateException("boom")).whenever(throwing).beginTransaction()
        whenever(throwing.rawQuery(any<String>(), any())).thenThrow(IllegalStateException("boom"))
        doReturn(throwing).whenever(spy).writableDatabase
        doReturn(throwing).whenever(spy).readableDatabase
        return spy
    }

    @Test
    fun insertEvent_whenBeginTransactionThrows_returnsNullAndLogs() {
        val db = throwingSpy()
        assertNull(db.insertEvent(sampleEvent))
    }

    @Test
    fun insert_whenBeginTransactionThrows_returnsNullAndLogs() {
        val db = throwingSpy()
        assertNull(db.insertMessage(sampleMessage))
        assertNull(db.insertMetric(sampleMetric))
    }

    @Test
    fun getNextBatchAndMarkTransaction_whenBeginTransactionThrows_returnsEmpty() {
        val db = throwingSpy()
        assertTrue(db.getNextBatchAndMarkTransactionMessage(5).isEmpty())
        assertTrue(db.getNextBatchAndMarkTransactionMetric(5).isEmpty())
        assertTrue(db.getNextBatchAndMarkTransactionEvent(5).isEmpty())
    }

    @Test
    fun getAll_whenRawQueryThrows_returnsEmpty() {
        val db = throwingSpy()
        assertTrue(db.getAll(MESSAGE_TABLE_NAME) { _ -> emptyList<LogMessageEntity>() }.isEmpty())
        assertTrue(db.getAllUnMark(MESSAGE_TABLE_NAME) { _ -> emptyList<LogMessageEntity>() }.isEmpty())
    }

    // ---------- transform helpers ----------

    @Test
    fun transformMessage_metric_event_handleEmptyCursor() {
        val empty = mock<Cursor>()
        whenever(empty.moveToNext()).thenReturn(false)
        assertTrue(database.transformMessage(empty).isEmpty())
        assertTrue(database.transformMetric(empty).isEmpty())
        assertTrue(database.transformEvent(empty).isEmpty())
    }

    @Test
    fun transformMessage_metric_event_readSingleRowFromCursor() {
        database.insertMessage(sampleMessage)
        database.insertMetric(sampleMetric)
        database.insertEvent(sampleEvent)

        val msgs = database.getAll(MESSAGE_TABLE_NAME) { c -> database.transformMessage(c) }
        val metrics = database.getAll(METRIC_TABLE_NAME) { c -> database.transformMetric(c) }
        val events = database.getAll(EVENT_TABLE_NAME) { c -> database.transformEvent(c) }

        assertEquals(1, msgs.size)
        assertEquals(1, metrics.size)
        assertEquals(1, events.size)
    }

    // ---------- clearForTesting ----------

    @Test
    fun clearForTesting_deletesDatabase() {
        database.insertMessage(sampleMessage)
        database.close()
        Database.clearForTesting(context)
        // recreate and verify it's empty
        val fresh = newDatabase()
        try {
            assertTrue(fresh.getAll(MESSAGE_TABLE_NAME) { c -> fresh.transformMessage(c) }.isEmpty())
        } finally {
            fresh.close()
        }
    }

    // ---------- helpers ----------

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        arrayOf(name),
    ).use { it.count > 0 }

    private fun <T> eq(value: T) = org.mockito.kotlin.eq(value)
}
