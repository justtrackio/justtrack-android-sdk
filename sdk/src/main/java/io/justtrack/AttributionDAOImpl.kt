package io.justtrack

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.TestGroupIdReaderTask.TestGroupId
import io.justtrack.database.AttributionEntity
import io.justtrack.database.Database.Companion.ATTRIBUTION_TABLE_NAME
import io.justtrack.log.Logger
import io.justtrack.versions.ApplicationVersionImpl
import org.json.JSONException
import org.json.JSONObject
import java.util.Date
import java.util.UUID

internal open class AttributionDAOImpl internal constructor(consoleLogger: Logger) : AttributionDAO {

    internal var logger: Logger = consoleLogger

    override fun createTable(db: SQLiteDatabase) {
        db.execSQL(
            "create table $ATTRIBUTION_TABLE_NAME (" +
                "$ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT Integer, " +
                "$ATTRIBUTION_INTEGRITY_SECRET text, " +
                "$ATTRIBUTION_IS_CREATE_FINISHED Integer, " +
                "$ATTRIBUTION_DATA_VERSION Integer, " +
                "$ATTRIBUTION_USER_ID text, " +
                "$ATTRIBUTION_INSTALL_ID text, " +
                "$ATTRIBUTION_USER_TYPE text, " +
                "$ATTRIBUTION_CAMPAIGN_ID Integer, " +
                "$ATTRIBUTION_CAMPAIGN_NAME text, " +
                "$ATTRIBUTION_CAMPAIGN_TYPE text, " +
                "$ATTRIBUTION_CAMPAIGN_ORGANIC Integer, " +
                "$ATTRIBUTION_TYPE text, " +
                "$ATTRIBUTION_CHANNEL_ID Integer, " +
                "$ATTRIBUTION_CHANNEL_NAME text, " +
                "$ATTRIBUTION_CHANNEL_INCENT Integer, " +
                "$ATTRIBUTION_NETWORK_ID Integer, " +
                "$ATTRIBUTION_NETWORK_NAME text, " +
                "$ATTRIBUTION_SOURCE_ID text, " +
                "$ATTRIBUTION_SOURCE_BUNDLE_ID text, " +
                "$ATTRIBUTION_SOURCE_PLACEMENT text, " +
                "$ATTRIBUTION_ADSET_ID text, " +
                "$ATTRIBUTION_CREATED_AT Integer, " +
                "$ATTRIBUTION_INSTALL_APP_VERSION String, " +
                "$ATTRIBUTION_INSTALL_APP_VERSION_CODE String, " +
                "$ATTRIBUTION_LAST_APP_VERSION String, " +
                "$ATTRIBUTION_LAST_APP_VERSION_CODE String, " +
                "$ATTRIBUTION_TEST_GROUP Integer, " +
                "$ATTRIBUTION_FIRST_ATTRIBUTION_AT Integer, " +
                "$ATTRIBUTION_LAST_ATTRIBUTION_AT Integer, " +
                "$ATTRIBUTION_LAST_OPEN_AT Integer, " +
                "$ATTRIBUTION_CONFIG text, " +
                "$ATTRIBUTION_REDOWNLOAD Integer " +
                ")",
        )

        // Insert an initial empty row
        val insertInitialRowSQL =
            "INSERT INTO $ATTRIBUTION_TABLE_NAME (" +
                "$ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT," +
                "$ATTRIBUTION_INTEGRITY_SECRET," +
                "$ATTRIBUTION_IS_CREATE_FINISHED, " +
                "$ATTRIBUTION_DATA_VERSION, " +
                "$ATTRIBUTION_USER_ID, " +
                "$ATTRIBUTION_INSTALL_ID, " +
                "$ATTRIBUTION_USER_TYPE, " +
                "$ATTRIBUTION_CAMPAIGN_ID, " +
                "$ATTRIBUTION_CAMPAIGN_NAME, " +
                "$ATTRIBUTION_CAMPAIGN_TYPE, " +
                "$ATTRIBUTION_CAMPAIGN_ORGANIC, " +
                "$ATTRIBUTION_TYPE, " +
                "$ATTRIBUTION_CHANNEL_ID, " +
                "$ATTRIBUTION_CHANNEL_NAME, " +
                "$ATTRIBUTION_CHANNEL_INCENT, " +
                "$ATTRIBUTION_NETWORK_ID, " +
                "$ATTRIBUTION_NETWORK_NAME, " +
                "$ATTRIBUTION_SOURCE_ID, " +
                "$ATTRIBUTION_SOURCE_BUNDLE_ID, " +
                "$ATTRIBUTION_SOURCE_PLACEMENT, " +
                "$ATTRIBUTION_ADSET_ID, " +
                "$ATTRIBUTION_CREATED_AT, " +
                "$ATTRIBUTION_INSTALL_APP_VERSION, " +
                "$ATTRIBUTION_INSTALL_APP_VERSION_CODE, " +
                "$ATTRIBUTION_LAST_APP_VERSION, " +
                "$ATTRIBUTION_LAST_APP_VERSION_CODE, " +
                "$ATTRIBUTION_TEST_GROUP, " +
                "$ATTRIBUTION_FIRST_ATTRIBUTION_AT, " +
                "$ATTRIBUTION_LAST_ATTRIBUTION_AT, " +
                "$ATTRIBUTION_LAST_OPEN_AT, " +
                "$ATTRIBUTION_CONFIG, " +
                "$ATTRIBUTION_REDOWNLOAD " +
                ") VALUES (NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, " +
                "NULL, NULL, NULL, NULL, NULL)"
        db.execSQL(insertInitialRowSQL)
    }

    override fun setLogger(logger: Logger) {
        this.logger = logger
    }

    override fun migrateFromStore(context: Context, writableDatabase: SQLiteDatabase): Boolean {
        var result = false
        if (Store.isMigratedToDB(context)) {
            return false
        }

        try {
            writableDatabase.beginTransaction()
            val storedAttribution = AttributionEntity(writableDatabase)

            if (!storedAttribution.isMigrated()) {
                migrateVersionFromInt(context)
                val sharedPreferenceData = AttributionEntity(Store.getAllData(context))

                val mergedAttributionEntity = mergeEntity(sharedPreferenceData, storedAttribution)

                val contentValues = mergedAttributionEntity.toContentValues()
                writableDatabase.update(
                    ATTRIBUTION_TABLE_NAME,
                    contentValues,
                    null,
                    null,
                )
                writableDatabase.setTransactionSuccessful()
                result = true
                Store.setMigratedToDB(context, true)
            } else {
                writableDatabase.setTransactionSuccessful()
                result = false
            }
        } catch (exception: Exception) {
            logger.warn("Unable to invoke migrateFromStore", exception)
        } finally {
            endTransaction(writableDatabase)
        }
        return result
    }

    override fun setAttributionFinished(
        writableDatabase: SQLiteDatabase,
        context: Context,
        response: AttributionResponse,
        testGroup: Int?,
        sdkConfig: String?,
    ) {
        val now = System.currentTimeMillis()
        try {
            writableDatabase.beginTransaction()
            val storedAttribution = AttributionEntity(writableDatabase)
            val firstAttributedAt = storedAttribution.firstAttributionAt ?: now
            val sdkConfigString = sdkConfig ?: ""

            val contentValues = AttributionEntity(
                isCreateFinished = VALUE_INTEGER_TRUE,
                version = writableDatabase.version,
                userId = response.getUserId().toString(),
                installId = response.getInstallId(),
                userType = response.getUserType(),
                campaignId = response.getCampaign().id,
                campaignName = response.getCampaign().name,
                campaignType = response.getCampaign().type,
                isCampaignOrganic = response.getCampaign().isOrganic.toInt(),
                type = response.getType(),
                channelId = response.getChannel().id,
                channelName = response.getChannel().name,
                channelIncent = response.getChannel().isIncent.toInt(),
                partnerId = response.getPartner().id,
                partnerName = response.getPartner().name,
                createdAt = response.getCreatedAt().time,
                firstAttributionAt = firstAttributedAt,
                lastAttributionAt = now,
                lastOpenAt = now,
                testGroup = testGroup ?: VALUE_NO_TEST_GROUP,
                sdkConfig = sdkConfigString,
                redownload = response.getRedownload().toInt(),
                sourceId = response.getSourceId(),
                sourceBundleId = response.getSourceBundleId(),
                sourcePlacement = response.getSourcePlacement(),
                adsetId = response.getAdsetId(),
                installAppVersion = storedAttribution.installAppVersion,
                installAppVersionCode = storedAttribution.installAppVersionCode,
                lastAppVersion = storedAttribution.lastAppVersion,
                lastAppVersionCode = storedAttribution.lastAppVersionCode,
                integritySecret = storedAttribution.integritySecret,
                isIntegrityTokenSent = storedAttribution.isIntegrityTokenSent,
            ).toContentValues()
            writableDatabase.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
        } catch (exception: Exception) {
            logger.warn("Unable to invoke setAttributionFinished", exception)
        } finally {
            endTransaction(writableDatabase)
        }
    }

    override fun getAttributionTimestamps(readableDatabase: SQLiteDatabase): AttributionTimestamps? {
        try {
            val storedAttribution = AttributionEntity(readableDatabase)
            val firstAttributedAt = storedAttribution.firstAttributionAt ?: -1
            val lastAttributedAt = storedAttribution.lastAttributionAt ?: firstAttributedAt
            val lastOpenAt = storedAttribution.lastOpenAt ?: lastAttributedAt
            return if (firstAttributedAt == -1L) {
                null
            } else {
                AttributionTimestamps(
                    firstAttributedAt,
                    lastAttributedAt,
                    lastOpenAt,
                )
            }
        } catch (exception: Exception) {
            logger.warn("Unable to invoke getAttributionTimestamps", exception)
        }
        return null
    }

    override fun getStoredOutput(context: Context, readableDatabase: SQLiteDatabase): AttributionOutput? {
        try {
            val storedData = AttributionEntity(readableDatabase)

            val userId: String = getUserId(readableDatabase) ?: ""
            val installId: String = getInstallId(readableDatabase) ?: ""

            val dataVersion = storedData.version ?: 0

            if (TextUtils.isNullOrEmpty(userId) || dataVersion != readableDatabase.version) {
                return null
            }

            val userType: String = storedData.userType ?: ""
            val campaignId: Int = storedData.campaignId ?: 0
            val campaignName: String = storedData.campaignName ?: ""
            val campaignType: String = storedData.campaignType ?: ""
            val campaignOrganic: Boolean = storedData.isCampaignOrganic?.toBoolean() ?: false
            val type: String = storedData.type ?: ""
            val channelId: Int = storedData.channelId ?: 0
            val channelName: String = storedData.channelName ?: ""
            val channelIncent: Boolean = storedData.channelIncent?.toBoolean() ?: false
            val partnerId: Int = storedData.partnerId ?: 0
            val partnerName: String = storedData.partnerName ?: ""
            val sourceId: String? = storedData.sourceId
            val sourceBundleId: String? = storedData.sourceBundleId
            val sourcePlacement: String? = storedData.sourcePlacement
            val adsetId: String? = storedData.adsetId
            val createdAt: Long = storedData.createdAt ?: System.currentTimeMillis()
            val reDownload: Boolean = storedData.redownload?.toBoolean() ?: false
            val sdkConfigString: String = storedData.sdkConfig ?: ""

            val sdkConfig: DTOAttributionOutputSdkConfig? = try {
                if (sdkConfigString.isEmpty()) {
                    null
                } else {
                    DTOAttributionOutputSdkConfig(
                        JSONObject(sdkConfigString),
                    )
                }
            } catch (e: JSONException) {
                null
            }

            val response: AttributionResponse = AttributionResponseImpl(
                UUID.fromString(userId),
                installId,
                userType,
                CampaignImpl(campaignId, campaignName, campaignType, campaignOrganic),
                type,
                ChannelImpl(channelId, channelName, channelIncent),
                PartnerImpl(partnerId, partnerName),
                if (TextUtils.isNullOrEmpty(sourceId)) null else sourceId,
                if (TextUtils.isNullOrEmpty(sourceBundleId)) null else sourceBundleId,
                if (TextUtils.isNullOrEmpty(sourcePlacement)) null else sourcePlacement,
                if (TextUtils.isNullOrEmpty(adsetId)) null else adsetId,
                Date(createdAt),
                reDownload,
            )

            var testGroup: Int? = storedData.testGroup ?: VALUE_NO_TEST_GROUP
            if (testGroup == VALUE_NO_TEST_GROUP) {
                testGroup = null
            }

            return AttributionOutput(response, null, testGroup, sdkConfig, false)
        } catch (exception: Exception) {
            logger.warn("Unable to invoke getStoredOutput", exception)
        }
        return null
    }

    override fun setTestGroupId(writableDatabase: SQLiteDatabase, testGroupId: Int?) {
        try {
            writableDatabase.beginTransaction()
            val contentValues = AttributionEntity(writableDatabase).copy(
                testGroup = testGroupId ?: VALUE_NO_TEST_GROUP,
            ).toContentValues()

            writableDatabase.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
        } catch (exception: Exception) {
            logger.warn("Unable to invoke setTestGroupId", exception)
        } finally {
            endTransaction(writableDatabase)
        }
    }

    override fun getTestGroupId(readableDatabase: SQLiteDatabase): TestGroupId? {
        try {
            val attributionEntity = AttributionEntity(readableDatabase)
            return if (attributionEntity.testGroup == null) {
                null
            } else if (attributionEntity.testGroup == VALUE_NO_TEST_GROUP) {
                TestGroupId(null)
            } else {
                TestGroupId(attributionEntity.testGroup)
            }
        } catch (exception: Exception) {
            logger.warn("Unable to invoke getTestGroupId", exception)
        }

        return null
    }

    override fun getSdkConfig(readableDatabase: SQLiteDatabase): String? {
        try {
            val sdkConfig = AttributionEntity(readableDatabase).sdkConfig

            return if (sdkConfig.isNullOrEmpty()) {
                null
            } else {
                sdkConfig
            }
        } catch (exception: Exception) {
            logger.warn("Unable to invoke sdkConfig", exception)
        }

        return null
    }

    override fun setLastOpen(writableDatabase: SQLiteDatabase, currentMs: Long) {
        try {
            writableDatabase.beginTransaction()
            val contentValues = AttributionEntity(writableDatabase).copy(
                lastOpenAt = currentMs,
            ).toContentValues()

            writableDatabase.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
        } catch (exception: Exception) {
            logger.warn("Unable to invoke setLastOpen", exception)
        } finally {
            endTransaction(writableDatabase)
        }
    }

    override fun getAppVersionUpdateInfo(writableDatabase: SQLiteDatabase, currentVersion: ApplicationVersion): AppVersionUpdateInfo {
        var result: AppVersionUpdateInfo? = null
        try {
            val storedData = AttributionEntity(writableDatabase)
            val atInstallVersionName: String? = storedData.installAppVersion
            val atInstallVersionCode: String? = storedData.installAppVersionCode
            writableDatabase.beginTransaction()
            if (atInstallVersionName == null && atInstallVersionCode == null) {
                val editedContentValues = storedData.copy(
                    installAppVersion = currentVersion.getVersionName(),
                    installAppVersionCode = currentVersion.getVersionCode(),
                    lastAppVersion = currentVersion.getVersionName(),
                    lastAppVersionCode = currentVersion.getVersionCode(),
                ).toContentValues()
                writableDatabase.update(
                    ATTRIBUTION_TABLE_NAME,
                    editedContentValues,
                    null,
                    null,
                )

                result = AppVersionUpdateInfo(
                    currentVersion,
                    currentVersion,
                    AppVersionUpdateKind.INSTALLED_APP,
                )
            } else {
                val lastVersionName = storedData.lastAppVersion ?: atInstallVersionName
                val lastVersionCode = storedData.lastAppVersionCode
                val editedContentValues = storedData.copy(
                    lastAppVersion = currentVersion.getVersionName(),
                    lastAppVersionCode = currentVersion.getVersionCode(),
                ).toContentValues()
                writableDatabase.update(
                    ATTRIBUTION_TABLE_NAME,
                    editedContentValues,
                    null,
                    null,
                )
                val lastVersion = if (lastVersionCode != null || lastVersionName != null) {
                    ApplicationVersionImpl(name = lastVersionName, code = lastVersionCode)
                } else {
                    ApplicationVersionImpl(name = atInstallVersionName, code = atInstallVersionCode)
                }
                result = AppVersionUpdateInfo(
                    ApplicationVersionImpl(name = atInstallVersionName, code = atInstallVersionCode),
                    lastVersion,
                    if (lastVersion == currentVersion) AppVersionUpdateKind.NO_CHANGE else AppVersionUpdateKind.UPDATED_APP,
                )
            }

            writableDatabase.setTransactionSuccessful()
        } catch (exception: Exception) {
            logger.warn("Unable to invoke getStoredSdkConfig", exception)
        } finally {
            endTransaction(writableDatabase)
        }

        return result ?: AppVersionUpdateInfo(
            currentVersion,
            currentVersion,
            AppVersionUpdateKind.INSTALLED_APP,
        )
    }

    override fun getInstallId(readableDatabase: SQLiteDatabase): String? {
        try {
            val installId = AttributionEntity(readableDatabase).installId
            return installId
        } catch (exception: Exception) {
            logger.warn("Unable to invoke getInstallId", exception)
        }

        return null
    }

    override fun setInstallId(writableDatabase: SQLiteDatabase, installId: String): Boolean {
        var isSuccess = false
        try {
            writableDatabase.beginTransaction()
            val contentValues = AttributionEntity(writableDatabase).copy(
                installId = installId,
            ).toContentValues()

            writableDatabase.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
            isSuccess = true
        } catch (exception: Exception) {
            logger.warn("Unable to invoke setInstallId", exception)
        } finally {
            endTransaction(writableDatabase)
        }

        return isSuccess
    }

    override fun getUserId(readableDatabase: SQLiteDatabase): String? {
        try {
            val userId = AttributionEntity(readableDatabase).userId
            return userId
        } catch (exception: Exception) {
            logger.warn("Unable to invoke getUserId", exception)
        }

        return null
    }

    override fun setUserId(writableDatabase: SQLiteDatabase, userId: String): Boolean {
        var isSuccess = false
        try {
            writableDatabase.beginTransaction()
            val contentValues = AttributionEntity(writableDatabase).copy(
                userId = userId,
            ).toContentValues()

            writableDatabase.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
            isSuccess = true
        } catch (exception: Exception) {
            logger.warn("Unable to invoke setUserId", exception)
        } finally {
            endTransaction(writableDatabase)
        }

        return isSuccess
    }

    override fun endTransaction(db: SQLiteDatabase) {
        if (db.isOpen) {
            try {
                db.endTransaction()
            } catch (error: Exception) {
                logger.warn("Failed to close transaction", error)
            }
        }
    }

    override fun mergeEntity(mainEntity: AttributionEntity, secondaryEntity: AttributionEntity): AttributionEntity {
        return AttributionEntity(
            isCreateFinished = mainEntity.isCreateFinished ?: secondaryEntity.isCreateFinished,
            version = mainEntity.version ?: secondaryEntity.version,
            userId = mainEntity.userId ?: secondaryEntity.userId,
            installId = mainEntity.installId ?: secondaryEntity.installId,
            userType = mainEntity.userType ?: secondaryEntity.userType,
            campaignId = mainEntity.campaignId ?: secondaryEntity.campaignId,
            campaignName = mainEntity.campaignName ?: secondaryEntity.campaignName,
            campaignType = mainEntity.campaignType ?: secondaryEntity.campaignType,
            isCampaignOrganic = mainEntity.isCampaignOrganic ?: secondaryEntity.isCampaignOrganic,
            type = mainEntity.type ?: secondaryEntity.type,
            channelId = mainEntity.channelId ?: secondaryEntity.channelId,
            channelName = mainEntity.channelName ?: secondaryEntity.channelName,
            channelIncent = mainEntity.channelIncent ?: secondaryEntity.channelIncent,
            partnerId = mainEntity.partnerId ?: secondaryEntity.partnerId,
            partnerName = mainEntity.partnerName ?: secondaryEntity.partnerName,
            sourceId = mainEntity.sourceId ?: secondaryEntity.sourceId,
            sourceBundleId = mainEntity.sourceBundleId ?: secondaryEntity.sourceBundleId,
            sourcePlacement = mainEntity.sourcePlacement ?: secondaryEntity.sourcePlacement,
            adsetId = mainEntity.adsetId ?: secondaryEntity.adsetId,
            createdAt = mainEntity.createdAt ?: secondaryEntity.createdAt,
            installAppVersion = mainEntity.installAppVersion ?: secondaryEntity.installAppVersion,
            installAppVersionCode = mainEntity.installAppVersionCode ?: secondaryEntity.installAppVersionCode,
            lastAppVersion = mainEntity.lastAppVersion ?: secondaryEntity.lastAppVersion,
            lastAppVersionCode = mainEntity.lastAppVersionCode ?: secondaryEntity.lastAppVersionCode,
            testGroup = mainEntity.testGroup ?: secondaryEntity.testGroup,
            firstAttributionAt = mainEntity.firstAttributionAt ?: secondaryEntity.firstAttributionAt,
            lastAttributionAt = mainEntity.lastAttributionAt ?: secondaryEntity.lastAttributionAt,
            lastOpenAt = mainEntity.lastOpenAt ?: secondaryEntity.lastOpenAt,
            sdkConfig = mainEntity.sdkConfig ?: secondaryEntity.sdkConfig,
            redownload = mainEntity.redownload ?: secondaryEntity.redownload,
            isIntegrityTokenSent = mainEntity.isIntegrityTokenSent ?: secondaryEntity.isIntegrityTokenSent,
            integritySecret = mainEntity.integritySecret ?: secondaryEntity.integritySecret,
        )
    }

    @VisibleForTesting
    override fun setIntegritySecret(writableDatabase: SQLiteDatabase, secret: String) {
        try {
            writableDatabase.beginTransaction()
            val contentValues = AttributionEntity(writableDatabase).copy(
                integritySecret = secret,
            ).toContentValues()

            writableDatabase.update(
                ATTRIBUTION_TABLE_NAME,
                contentValues,
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
        } catch (exception: Exception) {
            logger.warn("Unable to invoke setIntegritySecret", exception)
        } finally {
            endTransaction(writableDatabase)
        }
    }

    @VisibleForTesting
    override fun getAllAttribution(readableDatabase: SQLiteDatabase): AttributionEntity {
        return AttributionEntity(readableDatabase)
    }

    /***
     * SDK version before 4.6.0 are using application version as Integer instead of String.
     */
    private fun migrateVersionFromInt(context: Context) {
        Store.migrateVersionIntToString(context)
    }

    override fun dropFieldOperation(writableDatabase: SQLiteDatabase) {
        try {
            writableDatabase.beginTransaction()
            val storedData = AttributionEntity(writableDatabase)

            writableDatabase.execSQL("DROP TABLE $ATTRIBUTION_TABLE_NAME")
            createTable(writableDatabase)
            writableDatabase.update(
                ATTRIBUTION_TABLE_NAME,
                storedData.toContentValues(),
                null,
                null,
            )
            writableDatabase.setTransactionSuccessful()
        } catch (exception: Exception) {
            logger.warn("Unable to dropFieldOperation ${exception.message}", exception)
        } finally {
            writableDatabase.endTransaction()
        }
    }

    companion object {
        @JvmStatic internal val ATTRIBUTION_IS_CREATE_FINISHED = "is_create_finished"

        @JvmStatic internal val ATTRIBUTION_DATA_VERSION = "version"

        @JvmStatic internal val ATTRIBUTION_USER_ID = "user_id"

        @JvmStatic internal val ATTRIBUTION_INSTALL_ID = "install_id"

        @JvmStatic internal val ATTRIBUTION_USER_TYPE = "user_type"

        @JvmStatic internal val ATTRIBUTION_CAMPAIGN_ID = "campaign_id"

        @JvmStatic internal val ATTRIBUTION_CAMPAIGN_NAME = "campaign_name"

        @JvmStatic internal val ATTRIBUTION_CAMPAIGN_TYPE = "campaign_type"

        @JvmStatic internal val ATTRIBUTION_CAMPAIGN_ORGANIC = "is_campaign_organic"

        @JvmStatic internal val ATTRIBUTION_TYPE = "type"

        @JvmStatic internal val ATTRIBUTION_CHANNEL_ID = "channel_id"

        @JvmStatic internal val ATTRIBUTION_CHANNEL_NAME = "channel_name"

        @JvmStatic internal val ATTRIBUTION_CHANNEL_INCENT = "channel_incent"

        @JvmStatic internal val ATTRIBUTION_NETWORK_ID = "network_id"

        @JvmStatic internal val ATTRIBUTION_NETWORK_NAME = "network_name"

        @JvmStatic internal val ATTRIBUTION_SOURCE_ID = "source_id"

        @JvmStatic internal val ATTRIBUTION_SOURCE_BUNDLE_ID = "source_bundle_id"

        @JvmStatic internal val ATTRIBUTION_SOURCE_PLACEMENT = "source_placement"

        @JvmStatic internal val ATTRIBUTION_ADSET_ID = "adset_id"

        @JvmStatic internal val ATTRIBUTION_CREATED_AT = "created_at"

        @JvmStatic internal val ATTRIBUTION_INSTALL_APP_VERSION = "install_app_version"

        @JvmStatic internal val ATTRIBUTION_INSTALL_APP_VERSION_CODE = "install_app_version_code"

        @JvmStatic internal val ATTRIBUTION_LAST_APP_VERSION = "last_app_version"

        @JvmStatic internal val ATTRIBUTION_LAST_APP_VERSION_CODE = "last_app_version_code"

        @JvmStatic internal val ATTRIBUTION_TEST_GROUP = "test_group"

        @JvmStatic internal val ATTRIBUTION_FIRST_ATTRIBUTION_AT = "first_attribution_at"

        @JvmStatic internal val ATTRIBUTION_LAST_ATTRIBUTION_AT = "last_attribution_at"

        @JvmStatic internal val ATTRIBUTION_LAST_OPEN_AT = "last_open_at"

        @JvmStatic internal val ATTRIBUTION_CONFIG = "sdk_config"

        @JvmStatic internal val ATTRIBUTION_REDOWNLOAD = "redownload"

        @JvmStatic internal val ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT = "isIntegrityTokenSent"

        @JvmStatic internal val ATTRIBUTION_INTEGRITY_SECRET = "integritySecret"

        private const val VALUE_NO_TEST_GROUP = -1
        private const val VALUE_INTEGER_TRUE = 1
    }
}
