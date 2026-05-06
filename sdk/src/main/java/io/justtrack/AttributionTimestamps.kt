package io.justtrack

internal data class AttributionTimestamps(
    private val firstAttributionAt: Long,
    private val lastAttributionAt: Long,
    private val lastOpenAt: Long,
) {
    @JvmName("getFirstAttributionAt")
    internal fun getFirstAttributionAt(): Long {
        return firstAttributionAt
    }

    @JvmName("getLastAttributionAt")
    internal fun getLastAttributionAt(): Long {
        return lastAttributionAt
    }

    @JvmName("getLastOpenAt")
    internal fun getLastOpenAt(): Long {
        return lastOpenAt
    }
}
