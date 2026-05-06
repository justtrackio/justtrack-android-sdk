package io.justtrack

/**
 * Returned when subscribing to some kind of event. Can be used to unsubscribe from the subscription again.
 */
interface Subscription {
    /**
     * Unsubscribe from this subscription.
     */
    fun unsubscribe()
}
