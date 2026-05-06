package io.justtrack.config

internal interface RemoteConfigTimestamp {
    /**
     * Current timestamp in unix format in second.
     */
    fun getCurrentTimestamp(): Long

    /**
     * First attribution timestamp in unix format in second.
     */
    suspend fun getFirstAttributionTimestamp(): Long?

    /**
     * First sdk initialization timestamp in unix format in second.
     */
    fun getFirstInitializedAtTimestamp(): Long

    /**
     * Application installed timestamp in unix format in second.
     * https://developer.android.com/reference/android/content/pm/PackageInfo#firstInstallTime
     */
    fun getInstalledAtTimestamp(): Long?
}
