package io.justtrack

import android.content.Context
import io.justtrack.log.Logger
import io.justtrack.util.ExcludeFromJacocoGeneratedReport

/**
 * BillingTracker implementation for BillingClient version 8 and newer.
 */
@ExcludeFromJacocoGeneratedReport
internal class BillingTrackerEight(
    sdk: JustTrackSdkImpl,
    context: Context,
    logger: Logger,
) : BillingTrackerFive(
    sdk,
    context,
    logger,
    BillingTrackerFactory.BillingVersion.VERSION_8,
) {
    override fun extractProductDetails(response: Any): List<Any>? = extractBillingEightProductDetails(response)

    // BillingClient 8 removed queryPurchaseHistoryAsync. Purchase history is only used for
    // diagnostic logging and is not required to report the current purchase.
    override suspend fun getPurchaseHistory(skuType: String): List<Pair<String, String>>? = null
}

internal fun extractBillingEightProductDetails(response: Any): List<Any>? {
    val productDetails = response.javaClass.getMethod("getProductDetailsList").invoke(response)
    if (productDetails !is List<*>) return null
    return productDetails.filterNotNull().takeIf { it.size == productDetails.size }
}
