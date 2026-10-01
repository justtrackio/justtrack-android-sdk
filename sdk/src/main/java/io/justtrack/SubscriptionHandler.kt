package io.justtrack

internal fun interface SubscriptionHandler<L> {
    fun handle(listener: L)
}
