package io.justtrack

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.database.AttributionEntity
import io.justtrack.database.Database.Companion.ATTRIBUTION_TABLE_NAME
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.versions.ApplicationVersionImpl
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
internal class AttributionDAOImplUnitTest {
    private lateinit var context: Context
    private lateinit var database: SQLiteDatabase
    private lateinit var attributionDAO: AttributionDAOImpl

    private val exampleResponse = AttributionResponseImpl(
        UUID.fromString("1db3a1c1-e7e6-4994-949c-23241447e91b"),
        "44750b76-c8d2-4da9-9f25-5704e8cd491f",
        "acquisition",
        CampaignImpl("5", "test campaign", "acquisition", true),
        ChannelImpl(6, "test channel", true),
        PartnerImpl(7, "test network"),
        "sourceId",
        "sourceBundleId",
        "sourcePlacement",
        "adsetId",
        Date(1_700_000_000_000L),
        false,
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        Store.clearForTesting(context)
        database = SQLiteDatabase.create(null).apply {
            version = DATABASE_VERSION
        }
        attributionDAO = AttributionDAOImpl(context, TestLogger())
        attributionDAO.createTable(database)
    }

    @After
    fun tearDown() {
        Store.clearForTesting(context)
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun createTable_insertsEmptyAttributionRow() {
        val attribution = attributionDAO.getAllAttribution(database)

        Assert.assertFalse(attribution.isMigrated())
        Assert.assertNull(attribution.userId)
        Assert.assertNull(attribution.installId)
        Assert.assertNull(attribution.firstAttributionAt)
    }

    @Test
    fun setUserIdAndInstallId_preservesBothValues() {
        val userId = UUID.randomUUID().toString()
        val installId = UUID.randomUUID().toString()

        Assert.assertTrue(attributionDAO.setUserId(database, userId))
        Assert.assertTrue(attributionDAO.setInstallId(database, installId))

        Assert.assertEquals(userId, attributionDAO.getUserId(database))
        Assert.assertEquals(installId, attributionDAO.getInstallId(database))
    }

    @Test
    fun setAttributionFinished_storesOutputAndTimestamps() {
        val beforeAttribution = System.currentTimeMillis()

        attributionDAO.setAttributionFinished(database, exampleResponse)

        val storedOutput = attributionDAO.getStoredOutput(database)
        val timestamps = attributionDAO.getAttributionTimestamps(database)

        Assert.assertNotNull(storedOutput)
        Assert.assertEquals(exampleResponse, storedOutput!!.getAttributionResponse())
        Assert.assertNotNull(timestamps)
        Assert.assertTrue(timestamps!!.getFirstAttributionAt() >= beforeAttribution)
        Assert.assertTrue(timestamps.getLastAttributionAt() >= timestamps.getFirstAttributionAt())
        Assert.assertEquals(timestamps.getLastAttributionAt(), timestamps.getLastOpenAt())
    }

    @Test
    fun setAttributionFinished_preservesFirstAttributionTimestamp() {
        attributionDAO.setAttributionFinished(database, exampleResponse)
        val firstAttributionAt = attributionDAO.getAttributionTimestamps(database)!!.getFirstAttributionAt()

        attributionDAO.setAttributionFinished(database, exampleResponse)

        Assert.assertEquals(firstAttributionAt, attributionDAO.getAttributionTimestamps(database)!!.getFirstAttributionAt())
    }

    @Test
    fun setIntegritySecret_storesSecret() {
        val secret = "integrity-secret"

        attributionDAO.setIntegritySecret(database, secret)

        Assert.assertEquals(secret, attributionDAO.getAllAttribution(database).integritySecret)
    }

    @Test
    fun constructor_setsLogger() {
        val logger = RecordingLogger()

        val attributionDAO = AttributionDAOImpl(context, logger)

        Assert.assertSame(logger, attributionDAO.logger)
    }

    @Test
    fun loggerProperty_canBeReadAndWrittenDirectly() {
        val logger = RecordingLogger()

        attributionDAO.logger = logger

        Assert.assertSame(logger, attributionDAO.logger)
    }

    @Test
    fun setLogger_replacesLogger() {
        val logger = RecordingLogger()

        attributionDAO.setLogger(logger)

        Assert.assertSame(logger, attributionDAO.logger)
    }

    @Test
    fun migrateFromStore_whenAlreadyMigratedToDatabase_skipsMigration() {
        Store.setMigratedToDB(context, true)

        val migrated = attributionDAO.migrateFromStore(database)

        Assert.assertFalse(migrated)
    }

    @Test
    fun migrateFromStore_withoutStoredDatabaseAttribution_migratesSharedPreferences() {
        val userId = UUID.randomUUID().toString()
        val installId = UUID.randomUUID().toString()
        Store.setUserId(context, userId)
        Store.setInstallId(context, installId)

        val migrated = attributionDAO.migrateFromStore(database)

        Assert.assertTrue(migrated)
        Assert.assertTrue(Store.isMigratedToDB(context))
        Assert.assertEquals(userId, attributionDAO.getUserId(database))
        Assert.assertEquals(installId, attributionDAO.getInstallId(database))
    }

    @Test
    fun migrateFromStore_existingDatabaseAttribution_skipsMigration() {
        val userId = UUID.randomUUID().toString()
        Store.setUserId(context, UUID.randomUUID().toString())
        attributionDAO.setUserId(database, userId)

        val migrated = attributionDAO.migrateFromStore(database)

        Assert.assertFalse(migrated)
        Assert.assertFalse(Store.isMigratedToDB(context))
        Assert.assertEquals(userId, attributionDAO.getUserId(database))
    }

    @Test
    fun getStoredOutput_withoutCampaignExternalId_returnsNull() {
        val userId = UUID.fromString("1db3a1c1-e7e6-4994-949c-23241447e91b")
        updateAttribution(
            AttributionEntity(
                isCreateFinished = null,
                version = DATABASE_VERSION,
                userId = userId.toString(),
                installId = null,
                userType = null,
                campaignExternalId = null,
                campaignName = null,
                campaignType = null,
                isCampaignOrganic = null,
                type = null,
                channelId = null,
                channelName = null,
                channelIncent = null,
                partnerId = null,
                partnerName = null,
                sourceId = null,
                sourceBundleId = null,
                sourcePlacement = null,
                adsetId = null,
                createdAt = null,
                installAppVersion = null,
                installAppVersionCode = null,
                lastAppVersion = null,
                lastAppVersionCode = null,
                firstAttributionAt = null,
                lastAttributionAt = null,
                lastOpenAt = null,
                redownload = null,
                isIntegrityTokenSent = null,
                integritySecret = null,
            ),
        )

        Assert.assertNull(attributionDAO.getStoredOutput(database))
    }

    @Test
    fun getAttributionTimestamps_withoutAttribution_returnsNull() {
        Assert.assertNull(attributionDAO.getAttributionTimestamps(database))
    }

    @Test
    fun getAttributionTimestamps_withoutLastTimestamps_usesFirstAttributionTimestamp() {
        updateAttribution(
            completeAttributionEntity("timestamps").copy(
                firstAttributionAt = 100L,
                lastAttributionAt = null,
                lastOpenAt = null,
            ),
        )

        val timestamps = attributionDAO.getAttributionTimestamps(database)

        Assert.assertNotNull(timestamps)
        Assert.assertEquals(100L, timestamps!!.getFirstAttributionAt())
        Assert.assertEquals(100L, timestamps.getLastAttributionAt())
        Assert.assertEquals(100L, timestamps.getLastOpenAt())
    }

    @Test
    fun getStoredOutput_withoutUserId_returnsNull() {
        updateAttribution(
            completeAttributionEntity("stored-output").copy(
                version = DATABASE_VERSION,
                userId = null,
            ),
        )

        Assert.assertNull(attributionDAO.getStoredOutput(database))
    }

    @Test
    fun getStoredOutput_withStaleDataVersion_returnsNull() {
        updateAttribution(
            completeAttributionEntity("stored-output").copy(
                version = DATABASE_VERSION - 1,
                userId = UUID.randomUUID().toString(),
            ),
        )

        Assert.assertNull(attributionDAO.getStoredOutput(database))
    }

    @Test
    fun getStoredOutput_withoutDataVersion_returnsNull() {
        updateAttribution(
            completeAttributionEntity("stored-output").copy(
                version = null,
                userId = UUID.randomUUID().toString(),
            ),
        )

        Assert.assertNull(attributionDAO.getStoredOutput(database))
    }

    @Test
    fun setLastOpen_updatesOnlyLastOpenTimestamp() {
        val lastOpenAt = 1_800_000_000_000L

        attributionDAO.setAttributionFinished(database, exampleResponse)
        val timestampsBeforeLastOpen = attributionDAO.getAttributionTimestamps(database)!!
        attributionDAO.setLastOpen(database, lastOpenAt)

        val timestampsAfterLastOpen = attributionDAO.getAttributionTimestamps(database)

        Assert.assertNotNull(timestampsAfterLastOpen)
        Assert.assertEquals(timestampsBeforeLastOpen.getFirstAttributionAt(), timestampsAfterLastOpen!!.getFirstAttributionAt())
        Assert.assertEquals(timestampsBeforeLastOpen.getLastAttributionAt(), timestampsAfterLastOpen.getLastAttributionAt())
        Assert.assertEquals(lastOpenAt, timestampsAfterLastOpen.getLastOpenAt())
    }

    @Test
    fun getAppVersionUpdateInfo_tracksInstallUpdateAndNoChange() {
        val installedVersion = attributionDAO.getAppVersionUpdateInfo(database, ApplicationVersionImpl("4.2", "42"))
        val updatedVersion = attributionDAO.getAppVersionUpdateInfo(database, ApplicationVersionImpl("4.3", "43"))
        val unchangedVersion = attributionDAO.getAppVersionUpdateInfo(database, ApplicationVersionImpl("4.3", "43"))

        Assert.assertEquals("4.2", installedVersion.appInstallVersion.getVersionName())
        Assert.assertEquals("4.2", installedVersion.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.INSTALLED_APP, installedVersion.kind)

        Assert.assertEquals("4.2", updatedVersion.appInstallVersion.getVersionName())
        Assert.assertEquals("4.2", updatedVersion.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, updatedVersion.kind)

        Assert.assertEquals("4.2", unchangedVersion.appInstallVersion.getVersionName())
        Assert.assertEquals("4.3", unchangedVersion.appLastVersion.getVersionName())
        Assert.assertEquals(AppVersionUpdateKind.NO_CHANGE, unchangedVersion.kind)
    }

    @Test
    fun getAppVersionUpdateInfo_withOnlyInstallVersionCode_usesStoredCode() {
        updateAttribution(
            AttributionEntity(
                isCreateFinished = null,
                version = null,
                userId = null,
                installId = null,
                userType = null,
                campaignExternalId = null,
                campaignName = null,
                campaignType = null,
                isCampaignOrganic = null,
                type = null,
                channelId = null,
                channelName = null,
                channelIncent = null,
                partnerId = null,
                partnerName = null,
                sourceId = null,
                sourceBundleId = null,
                sourcePlacement = null,
                adsetId = null,
                createdAt = null,
                installAppVersion = null,
                installAppVersionCode = "42",
                lastAppVersion = null,
                lastAppVersionCode = null,
                firstAttributionAt = null,
                lastAttributionAt = null,
                lastOpenAt = null,
                redownload = null,
                isIntegrityTokenSent = null,
                integritySecret = null,
            ),
        )

        val updateInfo = attributionDAO.getAppVersionUpdateInfo(database, ApplicationVersionImpl("4.3", "43"))

        Assert.assertEquals("", updateInfo.appInstallVersion.getVersionName())
        Assert.assertEquals("42", updateInfo.appInstallVersion.getVersionCode())
        Assert.assertEquals("", updateInfo.appLastVersion.getVersionName())
        Assert.assertEquals("42", updateInfo.appLastVersion.getVersionCode())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, updateInfo.kind)
    }

    @Test
    fun getAppVersionUpdateInfo_withOnlyInstallVersionName_usesStoredName() {
        updateAttribution(
            completeAttributionEntity("version").copy(
                installAppVersion = "4.2",
                installAppVersionCode = null,
                lastAppVersion = null,
                lastAppVersionCode = null,
            ),
        )

        val updateInfo = attributionDAO.getAppVersionUpdateInfo(database, ApplicationVersionImpl("4.3", "43"))

        Assert.assertEquals("4.2", updateInfo.appInstallVersion.getVersionName())
        Assert.assertEquals("", updateInfo.appInstallVersion.getVersionCode())
        Assert.assertEquals("4.2", updateInfo.appLastVersion.getVersionName())
        Assert.assertEquals("", updateInfo.appLastVersion.getVersionCode())
        Assert.assertEquals(AppVersionUpdateKind.UPDATED_APP, updateInfo.kind)
    }

    @Test
    fun getAllAttribution_withoutRows_returnsEmptyAttribution() {
        database.execSQL("DELETE FROM $ATTRIBUTION_TABLE_NAME")

        val attribution = attributionDAO.getAllAttribution(database)

        Assert.assertFalse(attribution.isMigrated())
        Assert.assertNull(attribution.userId)
        Assert.assertNull(attribution.installId)
    }

    @Test
    fun endTransaction_whenDatabaseClosed_doesNothing() {
        database.close()

        attributionDAO.endTransaction(database)

        Assert.assertFalse(database.isOpen)
    }

    @Test
    fun endTransaction_whenEndTransactionThrows_logsAndDoesNotThrow() {
        val writableDatabase = mock<SQLiteDatabase>()
        whenever(writableDatabase.isOpen).thenReturn(true)
        doThrow(RuntimeException("not in transaction")).whenever(writableDatabase).endTransaction()

        attributionDAO.endTransaction(writableDatabase)
    }

    @Test
    fun dropFieldOperation_recreatesTableAndPreservesData() {
        attributionDAO.setAttributionFinished(database, exampleResponse)
        attributionDAO.setIntegritySecret(database, "integrity-secret")

        attributionDAO.dropFieldOperation(database)

        Assert.assertEquals(exampleResponse, attributionDAO.getStoredOutput(database)!!.getAttributionResponse())
        Assert.assertEquals("integrity-secret", attributionDAO.getAllAttribution(database).integritySecret)
    }

    @Test
    fun mergeEntity_prefersMainEntityValuesAndFallsBackToSecondaryEntity() {
        val mainEntity = AttributionEntity(
            isCreateFinished = 1,
            version = null,
            userId = "main-user-id",
            installId = null,
            userType = "main-user-type",
            campaignExternalId = null,
            campaignName = "main-campaign-name",
            campaignType = null,
            isCampaignOrganic = 1,
            type = null,
            channelId = 10,
            channelName = null,
            channelIncent = 1,
            partnerId = null,
            partnerName = "main-partner-name",
            sourceId = null,
            sourceBundleId = "main-source-bundle-id",
            sourcePlacement = null,
            adsetId = "main-adset-id",
            createdAt = null,
            installAppVersion = "main-install-version",
            installAppVersionCode = null,
            lastAppVersion = "main-last-version",
            lastAppVersionCode = null,
            firstAttributionAt = 100L,
            lastAttributionAt = null,
            lastOpenAt = 300L,
            redownload = null,
            isIntegrityTokenSent = 1,
            integritySecret = null,
        )
        val secondaryEntity = AttributionEntity(
            isCreateFinished = 0,
            version = DATABASE_VERSION,
            userId = "secondary-user-id",
            installId = "secondary-install-id",
            userType = "secondary-user-type",
            campaignExternalId = "20",
            campaignName = "secondary-campaign-name",
            campaignType = "secondary-campaign-type",
            isCampaignOrganic = 0,
            type = "secondary-type",
            channelId = 30,
            channelName = "secondary-channel-name",
            channelIncent = 0,
            partnerId = 40,
            partnerName = "secondary-partner-name",
            sourceId = "secondary-source-id",
            sourceBundleId = "secondary-source-bundle-id",
            sourcePlacement = "secondary-source-placement",
            adsetId = "secondary-adset-id",
            createdAt = 400L,
            installAppVersion = "secondary-install-version",
            installAppVersionCode = "secondary-install-code",
            lastAppVersion = "secondary-last-version",
            lastAppVersionCode = "secondary-last-code",
            firstAttributionAt = 500L,
            lastAttributionAt = 600L,
            lastOpenAt = 700L,
            redownload = 1,
            isIntegrityTokenSent = 0,
            integritySecret = "secondary-integrity-secret",
        )

        val mergedEntity = attributionDAO.mergeEntity(mainEntity, secondaryEntity)

        Assert.assertEquals(1, mergedEntity.isCreateFinished)
        Assert.assertEquals(DATABASE_VERSION, mergedEntity.version)
        Assert.assertEquals("main-user-id", mergedEntity.userId)
        Assert.assertEquals("secondary-install-id", mergedEntity.installId)
        Assert.assertEquals("main-user-type", mergedEntity.userType)
        Assert.assertEquals("20", mergedEntity.campaignExternalId)
        Assert.assertEquals("main-campaign-name", mergedEntity.campaignName)
        Assert.assertEquals("secondary-campaign-type", mergedEntity.campaignType)
        Assert.assertEquals(1, mergedEntity.isCampaignOrganic)
        Assert.assertEquals("secondary-type", mergedEntity.type)
        Assert.assertEquals(10, mergedEntity.channelId)
        Assert.assertEquals("secondary-channel-name", mergedEntity.channelName)
        Assert.assertEquals(1, mergedEntity.channelIncent)
        Assert.assertEquals(40, mergedEntity.partnerId)
        Assert.assertEquals("main-partner-name", mergedEntity.partnerName)
        Assert.assertEquals("secondary-source-id", mergedEntity.sourceId)
        Assert.assertEquals("main-source-bundle-id", mergedEntity.sourceBundleId)
        Assert.assertEquals("secondary-source-placement", mergedEntity.sourcePlacement)
        Assert.assertEquals("main-adset-id", mergedEntity.adsetId)
        Assert.assertEquals(400L, mergedEntity.createdAt)
        Assert.assertEquals("main-install-version", mergedEntity.installAppVersion)
        Assert.assertEquals("secondary-install-code", mergedEntity.installAppVersionCode)
        Assert.assertEquals("main-last-version", mergedEntity.lastAppVersion)
        Assert.assertEquals("secondary-last-code", mergedEntity.lastAppVersionCode)
        Assert.assertEquals(100L, mergedEntity.firstAttributionAt)
        Assert.assertEquals(600L, mergedEntity.lastAttributionAt)
        Assert.assertEquals(300L, mergedEntity.lastOpenAt)
        Assert.assertEquals(1, mergedEntity.redownload)
        Assert.assertEquals(1, mergedEntity.isIntegrityTokenSent)
        Assert.assertEquals("secondary-integrity-secret", mergedEntity.integritySecret)
    }

    @Test
    fun mergeEntity_prefersMainEntityValuesForEveryField() {
        val mainEntity = completeAttributionEntity("main")
        val secondaryEntity = completeAttributionEntity("secondary")

        val mergedEntity = attributionDAO.mergeEntity(mainEntity, secondaryEntity)

        Assert.assertEquals(mainEntity, mergedEntity)
    }

    @Test
    fun mergeEntity_fallsBackToSecondaryEntityForEveryField() {
        val mainEntity = AttributionEntity()
        val secondaryEntity = completeAttributionEntity("secondary")

        val mergedEntity = attributionDAO.mergeEntity(mainEntity, secondaryEntity)

        Assert.assertEquals(secondaryEntity, mergedEntity)
    }

    @Test
    fun operations_whenDatabaseAccessThrows_returnFallbackValues() {
        val failingDatabase = mock<SQLiteDatabase>()

        Assert.assertFalse(attributionDAO.migrateFromStore(failingDatabase))
        attributionDAO.setAttributionFinished(failingDatabase, exampleResponse)
        Assert.assertNull(attributionDAO.getAttributionTimestamps(failingDatabase))
        Assert.assertNull(attributionDAO.getStoredOutput(failingDatabase))
        attributionDAO.setLastOpen(failingDatabase, 1_800_000_000_000L)

        val currentVersion = ApplicationVersionImpl("4.3", "43")
        val updateInfo = attributionDAO.getAppVersionUpdateInfo(failingDatabase, currentVersion)
        Assert.assertEquals(currentVersion, updateInfo.appInstallVersion)
        Assert.assertEquals(currentVersion, updateInfo.appLastVersion)
        Assert.assertEquals(AppVersionUpdateKind.INSTALLED_APP, updateInfo.kind)

        Assert.assertNull(attributionDAO.getInstallId(failingDatabase))
        Assert.assertFalse(attributionDAO.setInstallId(failingDatabase, "install-id"))
        Assert.assertNull(attributionDAO.getUserId(failingDatabase))
        Assert.assertFalse(attributionDAO.setUserId(failingDatabase, "user-id"))
        attributionDAO.setIntegritySecret(failingDatabase, "integrity-secret")
        attributionDAO.dropFieldOperation(failingDatabase)
    }

    private fun updateAttribution(attributionEntity: AttributionEntity) {
        database.update(
            ATTRIBUTION_TABLE_NAME,
            attributionEntity.toContentValues(),
            null,
            null,
        )
    }

    private fun completeAttributionEntity(prefix: String): AttributionEntity {
        return AttributionEntity(
            isCreateFinished = 1,
            version = DATABASE_VERSION,
            userId = "$prefix-user-id",
            installId = "$prefix-install-id",
            userType = "$prefix-user-type",
            campaignExternalId = "10",
            campaignName = "$prefix-campaign-name",
            campaignType = "$prefix-campaign-type",
            isCampaignOrganic = 1,
            type = "$prefix-type",
            channelId = 20,
            channelName = "$prefix-channel-name",
            channelIncent = 1,
            partnerId = 30,
            partnerName = "$prefix-partner-name",
            sourceId = "$prefix-source-id",
            sourceBundleId = "$prefix-source-bundle-id",
            sourcePlacement = "$prefix-source-placement",
            adsetId = "$prefix-adset-id",
            createdAt = 40L,
            installAppVersion = "$prefix-install-version",
            installAppVersionCode = "$prefix-install-code",
            lastAppVersion = "$prefix-last-version",
            lastAppVersionCode = "$prefix-last-code",
            firstAttributionAt = 50L,
            lastAttributionAt = 60L,
            lastOpenAt = 70L,
            redownload = 1,
            isIntegrityTokenSent = 1,
            integritySecret = "$prefix-integrity-secret",
        )
    }

    private companion object {
        private const val DATABASE_VERSION = 8
    }

    private class RecordingLogger : Logger {
        override val fallback: Logger
            get() = this

        override fun debug(message: String, vararg fields: LoggerFields) = Unit

        override fun info(message: String, vararg fields: LoggerFields) = Unit

        override fun warn(message: String, vararg fields: LoggerFields) = Unit

        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit

        override fun error(message: String, vararg fields: LoggerFields) = Unit

        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit

        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
    }
}
