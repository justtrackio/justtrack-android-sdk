package io.justtrack.config

/**
 * Configuration settings for remote config.
 */
data class JusttrackRemoteConfigSettings(
    /**
     * The minimum interval between fetches in seconds.
     * If fetch is called before this interval has elapsed, cached values will be returned.
     * Default is 86400 (24 hours).
     */
    val minimumFetchIntervalInSeconds: Long,
)
