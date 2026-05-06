package io.justtrack

internal data class ProductPurchase(
    val productIds: List<String>,
    val purchaseToken: String,
    val quantity: Int,
    val orderId: String?,
    val purchaseJson: String,
)
