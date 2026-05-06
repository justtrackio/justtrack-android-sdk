package io.justtrack

import io.justtrack.attribution.Campaign
import io.justtrack.attribution.Channel
import io.justtrack.attribution.Partner
import java.util.Date
import java.util.UUID

/**
 * A representation of the attribution returned by the server.
 */
internal interface AttributionResponse {
    /**
     * Get the justtrack user identifier.
     *
     * @return The justtrack user identifier.
     */
    fun getUserId(): UUID

    /**
     * Get the unique id of the current install of that user.
     * The install id might change even though the user id remains the same.
     *
     * @return The install id for the current user.
     */
    fun getInstallId(): String

    /**
     * Get the type of the current install. Can be "acquisition" or "retargeting".
     *
     * @return The type of the current install.
     */
    fun getUserType(): String

    /**
     * Get the campaign to which this user was attributed to.
     *
     * @return The campaign the user was attributed to.
     */
    fun getCampaign(): Campaign

    /**
     * Get the type of the attribution.
     *
     * @return The type of the attribution.
     */
    fun getType(): String

    /**
     * Get the id, name, and incent flag of the channel the user was attributed to.
     *
     * @return The channel the user was attributed to.
     */
    fun getChannel(): Channel

    /**
     * Get the id and name of the partner the user was attributed to.
     *
     * @return The partner the user was attributed to.
     */
    fun getPartner(): Partner

    /**
     * Get the source id the user was attributed to, if any.
     *
     * @return The source id the user was attributed to.
     */
    fun getSourceId(): String?

    /**
     * Get the source bundle id the user was attributed to, if any.
     *
     * @return The source bundle id the user was attributed to.
     */
    fun getSourceBundleId(): String?

    /**
     * Get the source placement the user was attributed to, if any.
     *
     * @return The source placement the user was attributed to.
     */
    fun getSourcePlacement(): String?

    /**
     * Get the adset id the user was attributed to, if any.
     *
     * @return The adset id the user was attributed to.
     */
    fun getAdsetId(): String?

    /**
     * Get the date the attribution was created at.
     *
     * @return The date of the attribution.
     */
    fun getCreatedAt(): Date

    /**
     * Check whether the user have previously download the application or not.
     *
     * @return Whether the user have previously download the application or not.
     */
    fun getRedownload(): Boolean
}
