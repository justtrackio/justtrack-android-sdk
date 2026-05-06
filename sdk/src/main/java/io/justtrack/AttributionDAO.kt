package io.justtrack

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import io.justtrack.TestGroupIdReaderTask.TestGroupId
import io.justtrack.database.AttributionEntity
import io.justtrack.database.BaseDAO
import io.justtrack.log.Logger

internal interface AttributionDAO : BaseDAO {
    fun migrateFromStore(context: Context, writableDatabase: SQLiteDatabase): Boolean
    fun setAttributionFinished(
        writableDatabase: SQLiteDatabase,
        context: Context,
        response: AttributionResponse,
        testGroup: Int?,
        sdkConfig: String?,
    )
    fun getAttributionTimestamps(readableDatabase: SQLiteDatabase): AttributionTimestamps?
    fun getStoredOutput(context: Context, readableDatabase: SQLiteDatabase): AttributionOutput?
    fun setTestGroupId(writableDatabase: SQLiteDatabase, testGroupId: Int?)
    fun getTestGroupId(readableDatabase: SQLiteDatabase): TestGroupId?
    fun getSdkConfig(readableDatabase: SQLiteDatabase): String?
    fun setLastOpen(writableDatabase: SQLiteDatabase, currentMs: Long)
    fun getAppVersionUpdateInfo(writableDatabase: SQLiteDatabase, currentVersion: ApplicationVersion): AppVersionUpdateInfo

    fun getInstallId(readableDatabase: SQLiteDatabase): String?
    fun setInstallId(writableDatabase: SQLiteDatabase, installId: String): Boolean
    fun getUserId(readableDatabase: SQLiteDatabase): String?
    fun setUserId(writableDatabase: SQLiteDatabase, userId: String): Boolean

    fun mergeEntity(mainEntity: AttributionEntity, secondaryEntity: AttributionEntity): AttributionEntity

    @VisibleForTesting
    fun setIntegritySecret(writableDatabase: SQLiteDatabase, secret: String)

    @VisibleForTesting
    fun getAllAttribution(readableDatabase: SQLiteDatabase): AttributionEntity

    fun setLogger(logger: Logger)

    fun dropFieldOperation(writableDatabase: SQLiteDatabase)
}
