package io.justtrack.config

internal interface RemoteConfigStore {
    fun getCurrentFetchInterval(): Long
    fun getPreviousFetchTimeStamp(): Long?
    fun setPreviousFetchTimeStamp(previousFetchTimeStamp: Long)
    fun setMinimumIntervalInSecond(minimumIntervalInSecond: Long)
    fun getRetryAfterSeconds(): Int?
    fun setRetryAfterSeconds(retryAfterSeconds: Int)
    fun getStoredAssignments(): Map<String, Assignment>?
    fun setStoredAssignments(assignment: String?)
}
