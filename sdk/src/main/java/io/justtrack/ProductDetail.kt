package io.justtrack

import io.justtrack.events.Money
import io.justtrack.util.ExcludeFromJacocoGeneratedReport

@ExcludeFromJacocoGeneratedReport
internal data class ProductDetail(
    val productId: String,
    val money: Money,
)
