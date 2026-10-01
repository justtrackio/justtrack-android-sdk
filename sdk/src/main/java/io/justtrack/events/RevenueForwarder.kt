package io.justtrack.events

import io.justtrack.AsyncFuture
import io.justtrack.ads.AdImpression

internal interface RevenueForwarder {
    fun forwardAdImpression(adImpression: AdImpression): AsyncFuture<Void?>

    fun forwardInApp(productId: String, token: String, money: Money): Boolean

    fun forwardSubscription(subscriptionId: String, token: String, money: Money): Boolean
}
