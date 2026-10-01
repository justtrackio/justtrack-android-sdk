package io.justtrack

internal fun interface AttributionSubscriptionHandler {
    fun callAttributionSubscriptions(storedResponse: AttributionResponse)
}
