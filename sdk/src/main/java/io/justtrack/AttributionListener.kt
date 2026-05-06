package io.justtrack

import io.justtrack.attribution.Attribution

/**
 * Listener for attribution updates.
 */
interface AttributionListener {
    /**
     * Called every time we retrieve a new attribution (if you register before the first attribution
     * response is received it is also called for that response).
     *
     * @param attribution The current attribution of the user.
     */
    fun onAttributionReceived(attribution: Attribution)
}
