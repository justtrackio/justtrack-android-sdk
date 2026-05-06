package io.justtrack.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_ADSET_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CAMPAIGN_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CAMPAIGN_NAME
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CAMPAIGN_ORGANIC
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CAMPAIGN_TYPE
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CHANNEL_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CHANNEL_INCENT
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CHANNEL_NAME
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CONFIG
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_CREATED_AT
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_DATA_VERSION
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_FIRST_ATTRIBUTION_AT
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_INSTALL_APP_VERSION
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_INSTALL_APP_VERSION_CODE
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_INSTALL_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_INTEGRITY_SECRET
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_IS_CREATE_FINISHED
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_LAST_APP_VERSION
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_LAST_APP_VERSION_CODE
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_LAST_ATTRIBUTION_AT
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_LAST_OPEN_AT
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_NETWORK_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_NETWORK_NAME
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_REDOWNLOAD
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_SOURCE_BUNDLE_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_SOURCE_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_SOURCE_PLACEMENT
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_TEST_GROUP
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_TYPE
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_USER_ID
import io.justtrack.AttributionDAOImpl.Companion.ATTRIBUTION_USER_TYPE
import io.justtrack.database.Database.Companion.ATTRIBUTION_TABLE_NAME
import io.justtrack.getIntOrNull
import io.justtrack.getLongOrNull
import io.justtrack.getStringOrNull
import io.justtrack.toInt

internal data class AttributionEntity internal constructor(
    internal val isCreateFinished: Int?,
    internal val version: Int?,
    internal val userId: String?,
    internal val installId: String?,
    internal val userType: String?,
    internal val campaignId: Int?,
    internal val campaignName: String?,
    internal val campaignType: String?,
    internal val isCampaignOrganic: Int?,
    internal val type: String?,
    internal val channelId: Int?,
    internal val channelName: String?,
    internal val channelIncent: Int?,
    internal val partnerId: Int?,
    internal val partnerName: String?,
    internal val sourceId: String?,
    internal val sourceBundleId: String?,
    internal val sourcePlacement: String?,
    internal val adsetId: String?,
    internal val createdAt: Long?,
    internal val installAppVersion: String?,
    internal val installAppVersionCode: String?,
    internal val lastAppVersion: String?,
    internal val lastAppVersionCode: String?,
    internal val testGroup: Int?,
    internal val firstAttributionAt: Long?,
    internal val lastAttributionAt: Long?,
    internal val lastOpenAt: Long?,
    internal val sdkConfig: String?,
    internal val redownload: Int?,
    internal val isIntegrityTokenSent: Int?,
    internal val integritySecret: String?,
) {

    internal constructor(cursor: Cursor) : this(
        isCreateFinished = cursor.getIntOrNull(ATTRIBUTION_IS_CREATE_FINISHED),
        version = cursor.getIntOrNull(ATTRIBUTION_DATA_VERSION),
        userId = cursor.getStringOrNull(ATTRIBUTION_USER_ID),
        installId = cursor.getStringOrNull(ATTRIBUTION_INSTALL_ID),
        userType = cursor.getStringOrNull(ATTRIBUTION_USER_TYPE),
        campaignId = cursor.getIntOrNull(ATTRIBUTION_CAMPAIGN_ID),
        campaignName = cursor.getStringOrNull(ATTRIBUTION_CAMPAIGN_NAME),
        campaignType = cursor.getStringOrNull(ATTRIBUTION_CAMPAIGN_TYPE),
        isCampaignOrganic = cursor.getIntOrNull(ATTRIBUTION_CAMPAIGN_ORGANIC),
        type = cursor.getStringOrNull(ATTRIBUTION_TYPE),
        channelId = cursor.getIntOrNull(ATTRIBUTION_CHANNEL_ID),
        channelName = cursor.getStringOrNull(ATTRIBUTION_CHANNEL_NAME),
        channelIncent = cursor.getIntOrNull(ATTRIBUTION_CHANNEL_INCENT),
        partnerId = cursor.getIntOrNull(ATTRIBUTION_NETWORK_ID),
        partnerName = cursor.getStringOrNull(ATTRIBUTION_NETWORK_NAME),
        sourceId = cursor.getStringOrNull(ATTRIBUTION_SOURCE_ID),
        sourceBundleId = cursor.getStringOrNull(ATTRIBUTION_SOURCE_BUNDLE_ID),
        sourcePlacement = cursor.getStringOrNull(ATTRIBUTION_SOURCE_PLACEMENT),
        adsetId = cursor.getStringOrNull(ATTRIBUTION_ADSET_ID),
        createdAt = cursor.getLongOrNull(ATTRIBUTION_CREATED_AT),
        installAppVersion = cursor.getStringOrNull(ATTRIBUTION_INSTALL_APP_VERSION),
        installAppVersionCode = cursor.getStringOrNull(ATTRIBUTION_INSTALL_APP_VERSION_CODE),
        lastAppVersion = cursor.getStringOrNull(ATTRIBUTION_LAST_APP_VERSION),
        lastAppVersionCode = cursor.getStringOrNull(ATTRIBUTION_LAST_APP_VERSION_CODE),
        testGroup = cursor.getIntOrNull(ATTRIBUTION_TEST_GROUP),
        firstAttributionAt = cursor.getLongOrNull(ATTRIBUTION_FIRST_ATTRIBUTION_AT),
        lastAttributionAt = cursor.getLongOrNull(ATTRIBUTION_LAST_ATTRIBUTION_AT),
        lastOpenAt = cursor.getLongOrNull(ATTRIBUTION_LAST_OPEN_AT),
        sdkConfig = cursor.getStringOrNull(ATTRIBUTION_CONFIG),
        redownload = cursor.getIntOrNull(ATTRIBUTION_REDOWNLOAD),
        isIntegrityTokenSent = cursor.getIntOrNull(ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT),
        integritySecret = cursor.getStringOrNull(ATTRIBUTION_INTEGRITY_SECRET),
    )

    internal constructor(database: SQLiteDatabase) : this(
        database.query(
            ATTRIBUTION_TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                AttributionEntity(cursor)
            } else {
                AttributionEntity()
            }
        },
    )

    private constructor() : this(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
    )

    internal constructor(entity: AttributionEntity) : this(
        isCreateFinished = entity.isCreateFinished,
        version = entity.version,
        userId = entity.userId,
        installId = entity.installId,
        userType = entity.userType,
        campaignId = entity.campaignId,
        campaignName = entity.campaignName,
        campaignType = entity.campaignType,
        isCampaignOrganic = entity.isCampaignOrganic,
        type = entity.type,
        channelId = entity.channelId,
        channelName = entity.channelName,
        channelIncent = entity.channelIncent,
        partnerId = entity.partnerId,
        partnerName = entity.partnerName,
        sourceId = entity.sourceId,
        sourceBundleId = entity.sourceBundleId,
        sourcePlacement = entity.sourcePlacement,
        adsetId = entity.adsetId,
        createdAt = entity.createdAt,
        installAppVersion = entity.installAppVersion,
        installAppVersionCode = entity.installAppVersionCode,
        lastAppVersion = entity.lastAppVersion,
        lastAppVersionCode = entity.lastAppVersionCode,
        testGroup = entity.testGroup,
        firstAttributionAt = entity.firstAttributionAt,
        lastAttributionAt = entity.lastAttributionAt,
        lastOpenAt = entity.lastOpenAt,
        sdkConfig = entity.sdkConfig,
        redownload = entity.redownload,
        isIntegrityTokenSent = entity.isIntegrityTokenSent,
        integritySecret = entity.integritySecret,
    )

    internal constructor(map: Map<String, *>) : this(
        isCreateFinished = (map[ATTRIBUTION_IS_CREATE_FINISHED] as Boolean?)?.toInt(),
        version = map[ATTRIBUTION_DATA_VERSION] as Int?,
        userId = map[ATTRIBUTION_USER_ID] as String?,
        installId = map[ATTRIBUTION_INSTALL_ID] as String?,
        userType = map[ATTRIBUTION_USER_TYPE] as String?,
        campaignId = map[ATTRIBUTION_CAMPAIGN_ID] as Int?,
        campaignName = map[ATTRIBUTION_CAMPAIGN_NAME] as String?,
        campaignType = map[ATTRIBUTION_CAMPAIGN_TYPE] as String?,
        isCampaignOrganic = (map[ATTRIBUTION_CAMPAIGN_ORGANIC] as Boolean?)?.toInt(),
        type = map[ATTRIBUTION_TYPE] as String?,
        channelId = map[ATTRIBUTION_CHANNEL_ID] as Int?,
        channelName = map[ATTRIBUTION_CHANNEL_NAME] as String?,
        channelIncent = (map[ATTRIBUTION_CHANNEL_INCENT] as Boolean?)?.toInt(),
        partnerId = map[ATTRIBUTION_NETWORK_ID] as Int?,
        partnerName = map[ATTRIBUTION_NETWORK_NAME] as String?,
        sourceId = map[ATTRIBUTION_SOURCE_ID] as String?,
        sourceBundleId = map[ATTRIBUTION_SOURCE_BUNDLE_ID] as String?,
        sourcePlacement = map[ATTRIBUTION_SOURCE_PLACEMENT] as String?,
        adsetId = map[ATTRIBUTION_ADSET_ID] as String?,
        createdAt = map[ATTRIBUTION_CREATED_AT] as Long?,
        installAppVersion = map[ATTRIBUTION_INSTALL_APP_VERSION] as String?,
        installAppVersionCode = map[ATTRIBUTION_INSTALL_APP_VERSION_CODE] as String?,
        lastAppVersion = map[ATTRIBUTION_LAST_APP_VERSION] as String?,
        lastAppVersionCode = map[ATTRIBUTION_LAST_APP_VERSION_CODE] as String?,
        testGroup = map[ATTRIBUTION_TEST_GROUP] as Int?,
        firstAttributionAt = map[ATTRIBUTION_FIRST_ATTRIBUTION_AT] as Long?,
        lastAttributionAt = map[ATTRIBUTION_LAST_ATTRIBUTION_AT] as Long?,
        lastOpenAt = map[ATTRIBUTION_LAST_OPEN_AT] as Long?,
        sdkConfig = map[ATTRIBUTION_CONFIG] as String?,
        redownload = (map[ATTRIBUTION_REDOWNLOAD] as Boolean?)?.toInt(),
        isIntegrityTokenSent = map[ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT] as Int?,
        integritySecret = map[ATTRIBUTION_INTEGRITY_SECRET] as String?,
    )

    internal fun toContentValues(): ContentValues {
        return ContentValues().apply {
            put(ATTRIBUTION_IS_CREATE_FINISHED, isCreateFinished)
            put(ATTRIBUTION_DATA_VERSION, version)
            put(ATTRIBUTION_USER_ID, userId)
            put(ATTRIBUTION_INSTALL_ID, installId)
            put(ATTRIBUTION_USER_TYPE, userType)
            put(ATTRIBUTION_CAMPAIGN_ID, campaignId)
            put(ATTRIBUTION_CAMPAIGN_NAME, campaignName)
            put(ATTRIBUTION_CAMPAIGN_TYPE, campaignType)
            put(ATTRIBUTION_CAMPAIGN_ORGANIC, isCampaignOrganic)
            put(ATTRIBUTION_TYPE, type)
            put(ATTRIBUTION_CHANNEL_ID, channelId)
            put(ATTRIBUTION_CHANNEL_NAME, channelName)
            put(ATTRIBUTION_CHANNEL_INCENT, channelIncent)
            put(ATTRIBUTION_NETWORK_ID, partnerId)
            put(ATTRIBUTION_NETWORK_NAME, partnerName)
            put(ATTRIBUTION_SOURCE_ID, sourceId)
            put(ATTRIBUTION_SOURCE_BUNDLE_ID, sourceBundleId)
            put(ATTRIBUTION_SOURCE_PLACEMENT, sourcePlacement)
            put(ATTRIBUTION_ADSET_ID, adsetId)
            put(ATTRIBUTION_CREATED_AT, createdAt)
            put(ATTRIBUTION_INSTALL_APP_VERSION, installAppVersion)
            put(ATTRIBUTION_INSTALL_APP_VERSION_CODE, installAppVersionCode)
            put(ATTRIBUTION_LAST_APP_VERSION, lastAppVersion)
            put(ATTRIBUTION_LAST_APP_VERSION_CODE, lastAppVersionCode)
            put(ATTRIBUTION_TEST_GROUP, testGroup)
            put(ATTRIBUTION_FIRST_ATTRIBUTION_AT, firstAttributionAt)
            put(ATTRIBUTION_LAST_ATTRIBUTION_AT, lastAttributionAt)
            put(ATTRIBUTION_LAST_OPEN_AT, lastOpenAt)
            put(ATTRIBUTION_CONFIG, sdkConfig)
            put(ATTRIBUTION_REDOWNLOAD, redownload)
            put(ATTRIBUTION_IS_INTEGRITY_TOKEN_SENT, isIntegrityTokenSent)
            put(ATTRIBUTION_INTEGRITY_SECRET, integritySecret)
        }
    }

    /***
     * When we migrated to db userId or installId should be non-null.
     */
    internal fun isMigrated(): Boolean {
        return userId != null || installId != null
    }

    override fun toString(): String {
        return "AttributionEntity(" +
            "isCreateFinished=$isCreateFinished, " +
            "version=$version, " +
            "userId=$userId, " +
            "installId=$installId, " +
            "userType=$userType, " +
            "campaignId=$campaignId, " +
            "campaignName=$campaignName, " +
            "campaignType=$campaignType, " +
            "isCampaignOrganic=$isCampaignOrganic, " +
            "type=$type, " +
            "channelId=$channelId, " +
            "channelName=$channelName, " +
            "channelIncent=$channelIncent, " +
            "partnerId=$partnerId, " +
            "partnerName=$partnerName, " +
            "sourceId=$sourceId, " +
            "sourceBundleId=$sourceBundleId, " +
            "sourcePlacement=$sourcePlacement, " +
            "adsetId=$adsetId, " +
            "createdAt=$createdAt, " +
            "installAppVersion=$installAppVersion, " +
            "installAppVersionCode=$installAppVersionCode, " +
            "lastAppVersion=$lastAppVersion, " +
            "lastAppVersionCode=$lastAppVersionCode, " +
            "testGroup=$testGroup, " +
            "firstAttributionAt=$firstAttributionAt, " +
            "lastAttributionAt=$lastAttributionAt, " +
            "lastOpenAt=$lastOpenAt, " +
            "sdkConfig=$sdkConfig, " +
            "redownload=$redownload, " +
            "isIntegrityTokenSent=$isIntegrityTokenSent, " +
            "integritySecret=$integritySecret)"
    }
}
