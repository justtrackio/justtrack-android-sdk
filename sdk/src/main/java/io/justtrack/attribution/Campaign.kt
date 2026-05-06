package io.justtrack.attribution

/**
 * A representation of the campaign the user was attributed to.
 */
interface Campaign : IdString {
    /**
     * Get the type of the campaign. Can be "acquisition" or "retargeting".
     *
     * @return The type of the campaign.
     */
    val type: String

    /**
     * Check if the campaign is an organic "campaign" (i.e., the user directly installed from the
     * Play Store without seeing any ads).
     *
     * @return Whether the campaign is a campaign for organic users.
     */
    val isOrganic: Boolean
}
