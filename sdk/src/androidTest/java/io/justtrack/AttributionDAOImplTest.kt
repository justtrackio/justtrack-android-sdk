package io.justtrack

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.database.AttributionEntity
import io.justtrack.database.Database
import io.justtrack.log.Logger
import io.justtrack.publicInterface.SdkTest
import io.justtrack.util.ExecutorServiceFactory
import io.justtrack.versions.ApplicationVersionImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class AttributionDAOImplTest {
    private lateinit var context: Context
    private lateinit var httpClient: HttpClient
    private lateinit var executorBuilder: ExecutorServiceFactory

    private val exampleResponse: AttributionResponse = AttributionResponseImpl(
        UUID.fromString("1db3a1c1-e7e6-4994-949c-23241447e91b"),
        "44750b76-c8d2-4da9-9f25-5704e8cd491f",
        "acquisition",
        CampaignImpl(5, "test campaign", "acquisition", true),
        "test",
        ChannelImpl(6, "test channel", true),
        PartnerImpl(7, "test network"),
        "sourceId",
        "sourceBundleId",
        "sourcePlacement",
        "adsetId",
        Date(),
        false,
    )
    private val exampleTestGroup: Int = 2

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // clear all data to make sure we test a clean state
        val preferences = context.getSharedPreferences("justtrack-attribution", Context.MODE_PRIVATE)
        preferences.edit().clear().apply()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()

        httpClient = object : BaseTestHttpClient() {
            override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
                return Result.success(AttributionTest.testAttribution)
            }
        }
        executorBuilder = ExecutorServiceFactory {
            val executor =
                ThreadPoolExecutor(
                    10,
                    10,
                    60L,
                    TimeUnit.SECONDS,
                    LinkedBlockingDeque(),
                )
            executor.allowCoreThreadTimeOut(true)
            executor
        }
    }

    /***
     * Make sure the migration task runs before other AttributionDAO tasks.
     */
    @Test(timeout = 1_000L)
    fun migrateRunFirstTest() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val logger = LoggerImpl()
        val attributionDAO = BaseTestAttributionDAO()
        val database = Database(context, logger, attributionDAO)
        val databaseInterface = DatabaseInterface(logger, database)

        TestSdk(context, executorBuilder, httpClient, false, databaseInterface = databaseInterface)

        while (attributionDAO.methodExecuteOrder.size < 1) {
            delay(100)
        }
        val firstMethodCalled = attributionDAO.methodExecuteOrder.get(0)
        val secondMethodCalled = attributionDAO.methodExecuteOrder.get(1)
        Assert.assertEquals("createTable", firstMethodCalled)
        Assert.assertEquals("migrateFromStore", secondMethodCalled)
    }

    /***
     * Migration runs only once during the first session and is skipped in subsequent sessions.
     */
    @Test
    fun migrateRunOnceNormalTest() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val logger = LoggerImpl()
        val attributionDAO = AttributionDAOImpl(logger)
        val spy = spy(attributionDAO)
        val migrationResults = arrayListOf<Boolean>()
        doAnswer { invocation ->
            val result = invocation.callRealMethod() as Boolean
            migrationResults.add(result)
            result // Return the actual value
        }.whenever(spy).migrateFromStore(any(), any())

        val database = Database(context, logger, spy)

        database.attributionDAO.migrateFromStore(context, database.writableDatabase)
        database.attributionDAO.migrateFromStore(context, database.writableDatabase)

        Assert.assertTrue(migrationResults[0])
        Assert.assertFalse(migrationResults[1])
        Assert.assertFalse(migrationResults[2])
    }

    /***
     * Migration with no network runs only once.
     */
    @Test
    fun migrateOnceWithNoNetworkTest() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val logger = LoggerImpl()
        val attributionDAO = AttributionDAOImpl(logger)
        val spy = spy(attributionDAO)
        val migrationResults = arrayListOf<Boolean>()

        doAnswer { invocation ->
            val result = invocation.callRealMethod() as Boolean
            migrationResults.add(result)
            result // Return the actual value
        }.whenever(spy).migrateFromStore(any(), any())

        val database = Database(context, logger, spy)

        database.attributionDAO.migrateFromStore(context, database.writableDatabase)
        database.attributionDAO.migrateFromStore(context, database.writableDatabase)

        Assert.assertTrue(migrationResults.get(0))
        Assert.assertFalse(migrationResults.get(1))
        Assert.assertFalse(migrationResults.get(2))
    }

    /***
     * Merging data should not remove any existing data in table, but can replace.
     */
    @Test
    fun migrationMergeEntityCorrectlyTest() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val logger = LoggerImpl()
        val attributionDAO = AttributionDAOImpl(logger)
        val spy = spy(attributionDAO)
        var sharedPrefEntity: AttributionEntity? = null
        var dbEntity: AttributionEntity? = null
        val capture = argumentCaptor<AttributionEntity>()
        var mergeEntity: AttributionEntity? = null

        doAnswer { invocation ->
            sharedPrefEntity = invocation.arguments[0] as AttributionEntity
            dbEntity = invocation.arguments[1] as AttributionEntity
            mergeEntity = invocation.callRealMethod() as AttributionEntity
        }.whenever(spy).mergeEntity(capture.capture(), capture.capture())

        val userId = UUID.randomUUID()
        val installId = UUID.randomUUID()
        val legacyInstalledVersion = 440L
        val legacyLatestVersion = 442L
        Store.setInstallVersionLegacy(context, legacyInstalledVersion)
        Store.setLastVersionLegacy(context, legacyLatestVersion)
        Store.setUserId(context, userId.toString())
        Store.setInstallId(context, installId.toString())
        Store.setTestGroup(context, 1)

        val database = Database(context, logger, spy)
        val databaseInterface = DatabaseInterface(logger, database)

        TestSdk(context, executorBuilder, httpClient, false, databaseInterface = databaseInterface).start()
        Assert.assertNotNull(sharedPrefEntity)
        Assert.assertNotNull(dbEntity)
        Assert.assertNotNull(mergeEntity)
        Assert.assertEquals(userId.toString(), sharedPrefEntity!!.userId)
        Assert.assertEquals(installId.toString(), sharedPrefEntity!!.installId)
        Assert.assertEquals(legacyInstalledVersion.toString(), sharedPrefEntity!!.installAppVersion)
        Assert.assertEquals(legacyLatestVersion.toString(), sharedPrefEntity!!.lastAppVersion)

        Assert.assertEquals(userId.toString(), mergeEntity!!.userId)
        Assert.assertEquals(installId.toString(), mergeEntity!!.installId)
        Assert.assertEquals(legacyInstalledVersion.toString(), mergeEntity!!.installAppVersion)
        Assert.assertEquals(legacyLatestVersion.toString(), mergeEntity!!.lastAppVersion)
        Assert.assertEquals(1, mergeEntity!!.testGroup)
    }

    /***
     * Check if attribution is running and storing correctly
     */
    @Test
    fun storesCorrectly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseInterface = DatabaseInterface(context, LoggerImpl())
        var stored: AttributionOutput?
        databaseInterface.openAttribution().use {
            it.setAttributionFinished(context, exampleResponse, exampleTestGroup, null)
            stored = it.getStoredOutput(context)
        }

        Assert.assertNotNull(stored)
        Assert.assertEquals(exampleResponse, stored!!.getAttributionResponse())
        Assert.assertEquals(exampleTestGroup, stored!!.getTestGroup())
    }

    /***
     * Check that getAppVersionUpdateInfo returns and stores data properly.
     */
    @Test
    fun upgradeVersion() = runBlocking {
        var versionAtInstall: AppVersionUpdateInfo?
        val databaseInterface = DatabaseInterface(context, LoggerImpl())
        databaseInterface.openAttribution().use {
            versionAtInstall = it.getAppVersionUpdateInfo(ApplicationVersionImpl("4.2", "42"))
        }
        Assert.assertEquals("4.2", versionAtInstall!!.appInstallVersion.getVersionName())
        Assert.assertEquals("4.2", versionAtInstall!!.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.INSTALLED_APP, versionAtInstall!!.kind)

        var versionAtUpdate: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            versionAtUpdate = it.getAppVersionUpdateInfo(ApplicationVersionImpl("4.3", "43"))
        }
        Assert.assertEquals("4.2", versionAtUpdate!!.appInstallVersion.getVersionName())
        Assert.assertEquals("4.2", versionAtUpdate!!.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, versionAtUpdate!!.kind)

        var versionAfterUpdate: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            versionAfterUpdate = it.getAppVersionUpdateInfo(ApplicationVersionImpl("4.4", "44"))
        }
        Assert.assertEquals("4.2", versionAfterUpdate!!.appInstallVersion.getVersionName())
        Assert.assertEquals("4.3", versionAfterUpdate!!.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, versionAfterUpdate!!.kind)

        var versionAfterUpdate2: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            versionAfterUpdate2 = it.getAppVersionUpdateInfo(ApplicationVersionImpl("3.2", "32"))
        }
        Assert.assertEquals("4.2", versionAfterUpdate2!!.appInstallVersion.getVersionName())
        Assert.assertEquals("4.4", versionAfterUpdate2!!.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, versionAfterUpdate!!.kind)

        var versionNoChange: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            versionNoChange = it.getAppVersionUpdateInfo(ApplicationVersionImpl("3.2", "32"))
        }
        Assert.assertEquals("4.2", versionNoChange!!.appInstallVersion.getVersionName())
        Assert.assertEquals("3.2", versionNoChange!!.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.NO_CHANGE, versionNoChange!!.kind)
    }

    /***
     * Check that getAppVersionUpdateInfo executes properly even with outdated data types.
     */
    @Test
    fun migrateVersionScheme() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lastVersion = 230L
        val installVersion = 42L
        Store.setInstallVersionLegacy(context, installVersion)
        Store.setLastVersionLegacy(context, lastVersion)
        val database = Database(context, LoggerImpl())
        val databaseInterface = DatabaseInterface(LoggerImpl(), database)

        var version: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            version = it.getAppVersionUpdateInfo(ApplicationVersionImpl("2.3.1", "231"))
        }
        Assert.assertEquals(installVersion.toString(), version!!.appInstallVersion.getVersionName())
        Assert.assertEquals("", version!!.appInstallVersion.getVersionCode())
        Assert.assertEquals(lastVersion.toString(), version!!.appLastVersion.getVersionName())
        Assert.assertEquals("", version!!.appLastVersion.getVersionCode())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, version!!.kind)

        var version1: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            version1 = it.getAppVersionUpdateInfo(ApplicationVersionImpl("2.3.2", "232"))
        }
        Assert.assertEquals(installVersion.toString(), version1!!.appInstallVersion.getVersionName())
        Assert.assertEquals("", version1!!.appInstallVersion.getVersionCode())
        Assert.assertEquals("2.3.1", version1!!.appLastVersion.getVersionName())
        Assert.assertEquals("231", version1!!.appLastVersion.getVersionCode())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, version1!!.kind)

        var version2: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            version2 = it.getAppVersionUpdateInfo(ApplicationVersionImpl("2.3.2", "232"))
        }
        Assert.assertEquals(installVersion.toString(), version2!!.appInstallVersion.getVersionName())
        Assert.assertEquals("", version2!!.appInstallVersion.getVersionCode())
        Assert.assertEquals("2.3.2", version2!!.appLastVersion.getVersionName())
        Assert.assertEquals("232", version2!!.appLastVersion.getVersionCode())
        Assert.assertEquals(AppVersionUpdateKind.NO_CHANGE, version2!!.kind)
    }

    @Test
    fun getAppVersionAtInstall() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val sdk = JustTrackSdkBuilder((context.applicationContext as Application), SdkTest.API_TOKEN).build()
        val version = sdk.appVersionAtInstall.await()
        Assert.assertEquals("", version.getVersionName())
        // by default DeviceInfo "info.getLongVersionCode()" returns 0
        Assert.assertEquals("0", version.getVersionCode())
        sdk.shutdown()
    }

    @Test
    fun keepsVersion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseInterface = DatabaseInterface(context, LoggerImpl())
        var versionAtInstall: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            versionAtInstall = it.getAppVersionUpdateInfo(ApplicationVersionImpl("4.2", "42"))
        }
        Assert.assertEquals("4.2", versionAtInstall!!.appInstallVersion.getVersionName())
        Assert.assertEquals("4.2", versionAtInstall!!.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.INSTALLED_APP, versionAtInstall!!.kind)

        var versionAtUpdate: AppVersionUpdateInfo?
        databaseInterface.openAttribution().use {
            it.setAttributionFinished(context, exampleResponse, exampleTestGroup, null)
            versionAtUpdate = it.getAppVersionUpdateInfo(ApplicationVersionImpl("4.3", "43"))
        }
        Assert.assertEquals("4.2", versionAtInstall!!.appInstallVersion.getVersionName())
        Assert.assertEquals("4.2", versionAtInstall!!.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, versionAtUpdate!!.kind)

        var timestamps: AttributionTimestamps?
        databaseInterface.openAttribution().use {
            timestamps = it.getAttributionTimestamps()
        }
        Assert.assertNotNull(timestamps)
        val age = timestamps!!.getLastAttributionAt() - timestamps!!.getFirstAttributionAt()
        Assert.assertEquals(0L, age)
    }

    @Test
    fun tracksTime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseInterface = DatabaseInterface(context, LoggerImpl())
        databaseInterface.openAttribution().use {
            it.setAttributionFinished(context, exampleResponse, exampleTestGroup, null)
        }
        try {
            Thread.sleep(500)
        } catch (exception: InterruptedException) {
            Assert.fail("Unexpected error " + exception.message)
        }
        databaseInterface.openAttribution().use {
            it.setAttributionFinished(context, exampleResponse, exampleTestGroup, null)
        }

        var timestamps: AttributionTimestamps?
        databaseInterface.openAttribution().use {
            timestamps = it.getAttributionTimestamps()
        }

        Assert.assertNotNull(timestamps)
        val age = timestamps!!.getLastAttributionAt() - timestamps!!.getFirstAttributionAt()
        Assert.assertTrue("$age should be >= 500", age >= 500L)
    }

    @Test
    fun handleNoAttributionAge() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseInterface = DatabaseInterface(context, LoggerImpl())

        var timestamps: AttributionTimestamps?
        databaseInterface.openAttribution().use {
            timestamps = it.getAttributionTimestamps()
        }
        Assert.assertNull(timestamps)
    }

    internal open class BaseTestAttributionDAO : AttributionDAO {
        val methodExecuteOrder = arrayListOf<String>()
        val migrateRunCount = AtomicInteger(0)
        override fun migrateFromStore(context: Context, writableDatabase: SQLiteDatabase): Boolean {
            migrateRunCount.incrementAndGet()
            methodExecuteOrder.add("migrateFromStore")
            return true
        }

        override fun setAttributionFinished(
            writableDatabase: SQLiteDatabase,
            context: Context,
            response: AttributionResponse,
            testGroup: Int?,
            sdkConfig: String?,
        ) {
            methodExecuteOrder.add("setAttributionFinished")
        }

        override fun getAttributionTimestamps(readableDatabase: SQLiteDatabase): AttributionTimestamps? {
            methodExecuteOrder.add("getAttributionTimestamps")
            return null
        }

        override fun getStoredOutput(context: Context, readableDatabase: SQLiteDatabase): AttributionOutput? {
            methodExecuteOrder.add("getStoredOutput")
            return null
        }

        override fun setTestGroupId(writableDatabase: SQLiteDatabase, testGroupId: Int?) {
            methodExecuteOrder.add("setTestGroupId")
        }

        override fun getTestGroupId(readableDatabase: SQLiteDatabase): TestGroupIdReaderTask.TestGroupId? {
            methodExecuteOrder.add("getTestGroupId")
            return null
        }

        override fun getSdkConfig(readableDatabase: SQLiteDatabase): String? {
            methodExecuteOrder.add("getSdkConfig")
            return null
        }

        override fun setLastOpen(writableDatabase: SQLiteDatabase, currentMs: Long) {
            methodExecuteOrder.add("setLastOpen")
        }

        override fun getAppVersionUpdateInfo(writableDatabase: SQLiteDatabase, currentVersion: ApplicationVersion): AppVersionUpdateInfo {
            methodExecuteOrder.add("getAppVersionUpdateInfo")
            return AppVersionUpdateInfo(currentVersion, currentVersion, AppVersionUpdateKind.INSTALLED_APP)
        }

        override fun getInstallId(readableDatabase: SQLiteDatabase): String? {
            methodExecuteOrder.add(("getInstallId"))
            return null
        }

        override fun setInstallId(writableDatabase: SQLiteDatabase, installId: String): Boolean {
            methodExecuteOrder.add(("setInstallId"))
            return true
        }

        override fun getUserId(readableDatabase: SQLiteDatabase): String? {
            methodExecuteOrder.add(("getUserId"))
            return null
        }

        override fun setUserId(writableDatabase: SQLiteDatabase, userId: String): Boolean {
            methodExecuteOrder.add(("setUserId"))
            return true
        }

        override fun createTable(db: SQLiteDatabase) {
            methodExecuteOrder.add("createTable")
        }

        override fun endTransaction(writeableDatabase: SQLiteDatabase) {}

        override fun mergeEntity(mainEntity: AttributionEntity, secondaryEntity: AttributionEntity): AttributionEntity {
            return secondaryEntity
        }

        override fun setIntegritySecret(writableDatabase: SQLiteDatabase, secret: String) {
            methodExecuteOrder.add("setIntegritySecret")
        }

        override fun getAllAttribution(readableDatabase: SQLiteDatabase): AttributionEntity {
            methodExecuteOrder.add("getAllAttribution")
            return AttributionEntity(HashMap<String, String>())
        }

        override fun setLogger(logger: Logger) {
            // nop
        }

        override fun dropFieldOperation(writableDatabase: SQLiteDatabase) {
            // nop
        }
    }
}
