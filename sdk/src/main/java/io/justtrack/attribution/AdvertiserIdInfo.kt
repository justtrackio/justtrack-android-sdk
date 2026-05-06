package io.justtrack.attribution

/**
 * The result of reading an advertiser id can be one of three possibilities:
 * * The advertiser id was successfully read
 * * The user limited ad tracking and the OS enforces this (late 2021: Android 12, early 2022: all devices)
 * * Reading the advertiser id failed
 *
 *
 * In the first case there will be an advertiser id available. In the second case, the id will be null
 * and [.isLimitedAdTracking] will report true. In the last case the advertiser id will also be
 * null, but ad tracking will not be reported as limited.
 */
interface AdvertiserIdInfo {
    /**
     * Retrieve the advertiser id of the user or null if it could not be read (user limited tracking
     * and the OS enforces it or an error occurred).
     *
     * @return The advertiser id of the user or null.
     */
    val advertiserId: String?

    /**
     * Did the user limit ad tracking? This can also be reported as true while the advertiser id is
     * available. In that case the OS does not enforce the limit yet.
     *
     * @return Whether ad tracking is limited.
     */
    val isLimitedAdTracking: Boolean
}
