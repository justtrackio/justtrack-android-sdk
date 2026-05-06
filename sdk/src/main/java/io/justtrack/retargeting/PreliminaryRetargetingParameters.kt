package io.justtrack.retargeting

import io.justtrack.AsyncFuture
import io.justtrack.attribution.Attribution

/**
 * Retargeting parameters extracted from an [android.content.Intent]. Might carry more parameters
 * than it normally would if the browser did add additional parameters.
 *
 *
 * You can validate these parameters with the justtrack backend.
 */
interface PreliminaryRetargetingParameters : RetargetingParameters {
    /**
     * Find out whether these possible retargeting parameters turn out to be valid by calling the
     * justtrack backend.
     *
     * @return A future which returns the final retargeting parameters and the corresponding
     * attribution response.
     */
    fun validate(): AsyncFuture<ValidateResult?>

    /** The result of validating preliminary retargeting parameters against the justtrack backend. */
    interface ValidateResult {
        /**
         * True exactly when [.validParameters] returns a non-null value.
         *
         * @return Whether [.validParameters] returns non-null.
         */
        val isValid: Boolean

        /**
         * If non-null, the parameters as returned by the backend.
         *
         *
         * If null, the backend did not consider this a retargeting attribution.
         *
         * @return The retargeting parameters returned by the backend, if any.
         */
        fun validParameters(): RetargetingParameters?

        /**
         * Returns the attribution we retrieved from the backend while validating the preliminary
         * retargeting parameters.
         *
         * @return The attribution response.
         */
        val attribution: Attribution
    }
}
