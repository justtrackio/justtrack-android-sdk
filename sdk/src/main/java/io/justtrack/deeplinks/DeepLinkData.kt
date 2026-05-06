package io.justtrack.deeplinks

import android.net.Uri

/**
 * Represents the data associated with a deep link received by the application.
 */
interface DeepLinkData {
    /** The parsed [Uri] of the deep link. */
    val uri: Uri
}
