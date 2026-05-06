package io.justtrack.retargeting

import android.net.Uri

/**
 * If the app was started as part of a retargeting campaign (either the first app open after it was
 * installed again for that campaign or because it was launched from a retargeting intent link.
 */
interface RetargetingParameters {
    /**
     * Was the app already installed when the user performed the click?
     *
     * @return True if the app was already installed.
     */
    fun wasAlreadyInstalled(): Boolean

    /**
     * Get the URI of the retargeting click which triggered the app to get launched.
     *
     *
     * null if the click does not contain a valid URI.
     *
     * @return The URI of the retargeting click.
     */
    val uri: Uri?

    /**
     * Get a read-only map of parameters attached to the retargeting click which caused the app to get launched.
     *
     * @return A read-only map of parameters from the retargeting click.
     */
    val parameters: Map<String, String>

    /**
     * Get the promo_code parameter from the parameter map.
     *
     *
     * null if the parameter is not set or empty.
     *
     * @return The promo_code parameter from the parameter map.
     */
    val promotionParameter: String?
}
