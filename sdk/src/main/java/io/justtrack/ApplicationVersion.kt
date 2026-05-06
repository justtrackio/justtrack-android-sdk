package io.justtrack

/**
 * Provides version information about the host application.
 */
interface ApplicationVersion {
    /** Returns the human-readable version name (e.g. "1.2.3"). */
    fun getVersionName(): String

    /** Returns the numeric version code as a string. */
    fun getVersionCode(): String
}
