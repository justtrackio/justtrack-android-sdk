package io.justtrack.attribution

import java.util.Date

/**
 * Holds the attribution data for the current user, including campaign, channel, partner,
 * and install-type information returned by the justtrack backend.
 */
interface Attribution {
    /**
     * Get the type of the current install. Can be "acquisition" or "retargeting".
     *
     * @return The type of the current install.
     */
    val userType: String

    /**
     * Get the campaign to which this user was attributed to.
     *
     * @return The campaign the user was attributed to.
     */
    val campaign: Campaign

    /**
     * Get the id, name, and incent flag of the channel the user was attributed to.
     *
     * @return The channel the user was attributed to.
     */
    val channel: Channel

    /**
     * Get the id and name of the partner the user was attributed to.
     *
     * @return The partner the user was attributed to.
     */
    val partner: Partner

    /**
     * Get the source id the user was attributed to, if any.
     *
     * @return The source id the user was attributed to.
     */
    val sourceId: String?

    /**
     * Get the source bundle id the user was attributed to, if any.
     *
     * @return The source bundle id the user was attributed to.
     */
    val sourceBundleId: String?

    /**
     * Get the source placement the user was attributed to, if any.
     *
     * @return The source placement the user was attributed to.
     */
    val sourcePlacement: String?

    /**
     * Get the adset id the user was attributed to, if any.
     *
     * @return The adset id the user was attributed to.
     */
    val adsetId: String?

    /**
     * Get the date the attribution was created at.
     *
     * @return The date of the attribution.
     */
    val createdAt: Date

    /**
     * Check whether the user have previously download the application or not.
     *
     * @return Whether the user have previously download the application or not.
     */
    val isRedownload: Boolean
}
