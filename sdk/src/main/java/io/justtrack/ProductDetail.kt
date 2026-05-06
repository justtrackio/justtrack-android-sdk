package io.justtrack

import io.justtrack.events.Money

internal data class ProductDetail(
    val productId: String,
    val money: Money,
)
