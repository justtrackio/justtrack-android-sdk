package io.justtrack.attribution

/**
 * A representation of the channel the user was attributed to.
 */
interface Channel : IdString {
    /**
     * Get whether the channel was marked as giving the user an additional incentive to install the app.
     *
     * @return The incent flag on the channel.
     */
    val isIncent: Boolean
}
