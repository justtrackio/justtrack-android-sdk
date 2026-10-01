package io.justtrack

import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import io.justtrack.database.AttributionEntity
import io.justtrack.database.BaseDAO
import io.justtrack.log.Logger

internal interface AttributionDAO : BaseDAO {
    fun migrateFromStore(writableDatabase: SQLiteDatabase): Boolean
    fun setAttributionFinished(writableDatabase: SQLiteDatabase, response: AttributionResponse)
    fun getAttributionTimestamps(readableDatabase: SQLiteDatabase): AttributionTimestamps?
    fun getStoredOutput(readableDatabase: SQLiteDatabase): AttributionOutput?
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

    fun migrateAttributionToV8(writableDatabase: SQLiteDatabase)
    fun dropFieldOperation(writableDatabase: SQLiteDatabase)
}
