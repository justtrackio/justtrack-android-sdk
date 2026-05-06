package io.justtrack

import android.content.Context

internal interface DatabaseAttributionInterface : AutoCloseable {
    suspend fun setIntegrityTokenSent(isSent: Boolean): Boolean

    suspend fun isIntegrityTokenSent(): Boolean

    suspend fun setIntegritySecret(token: String): Boolean

    suspend fun getIntegritySecret(): String?

    suspend fun setTestGroupId(testGroupId: Int?): Boolean

    suspend fun getTestGroupId(): TestGroupIdReaderTask.TestGroupId?

    suspend fun setLastOpen(currentMs: Long): Boolean

    suspend fun setAttributionFinished(context: Context, response: AttributionResponse, testGroup: Int?, sdkConfig: String?): Boolean

    suspend fun getAttributionTimestamps(): AttributionTimestamps?

    suspend fun getStoredOutput(context: Context): AttributionOutput?

    suspend fun getSdkConfig(): String?

    suspend fun getAppVersionUpdateInfo(currentApplicationVersion: ApplicationVersion): AppVersionUpdateInfo?

    suspend fun getInstallId(): String?

    suspend fun getUserId(): String?

    suspend fun setInstallId(installId: String): Boolean

    suspend fun setUserId(userId: String): Boolean
}
