package io.justtrack

/**
 * Thrown when the attribution request to the justtrack backend fails, for example due to
 * an invalid API token or a network error.
 */
class AttributionException internal constructor(cause: Throwable) : Exception(
    if (wasApiTokenInvalid(cause)) {
        BadResponseException.formatErrorBox("Attribution can not be performed with an invalid API token.")
    } else {
        "Attribution post request failed"
    },
    cause,
) {
    /**
     * Check if the attribution failed because the app used an invalid API token.
     *
     * @return True if the API token was invalid.
     */
    fun wasApiTokenInvalid(): Boolean {
        val cause = cause

        return cause != null && wasApiTokenInvalid(cause)
    }

    /** Internal helpers for inspecting the cause chain. */
    companion object {
        private const val HTTP_UNAUTHORIZED = 401

        private fun wasApiTokenInvalid(cause: Throwable): Boolean {
            if (cause is BadResponseException) {
                return cause.responseCode == HTTP_UNAUTHORIZED
            }

            val nestedCause = cause.cause

            return nestedCause != null && wasApiTokenInvalid(nestedCause)
        }
    }
}
