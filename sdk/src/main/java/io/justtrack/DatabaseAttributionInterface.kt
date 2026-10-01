package io.justtrack

internal interface DatabaseAttributionInterface : AutoCloseable {
    suspend fun setIntegrityTokenSent(isSent: Boolean): Boolean

    suspend fun isIntegrityTokenSent(): Boolean

    suspend fun setIntegritySecret(token: String): Boolean

    suspend fun getIntegritySecret(): String?

    suspend fun setLastOpen(currentMs: Long): Boolean

    suspend fun setAttributionFinished(response: AttributionResponse): Boolean

    suspend fun getAttributionTimestamps(): AttributionTimestamps?

    suspend fun getStoredOutput(): AttributionOutput?

    suspend fun getAppVersionUpdateInfo(currentApplicationVersion: ApplicationVersion): AppVersionUpdateInfo?

    suspend fun getInstallId(): String?

    suspend fun getUserId(): String?

    suspend fun setInstallId(installId: String): Boolean

    suspend fun setUserId(userId: String): Boolean
}
