package io.justtrack

import android.content.Context
import androidx.core.util.Consumer
import io.justtrack.events.Money
import io.justtrack.log.Logger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * This BillingTracker implementation will be used if the client is using BillingClient version 5 & 6.
 * Since BillingClient V6.0 does not deprecate existing API or adding any additional API for tracking IAP.
 */
internal class BillingTrackerFive(
    sdk: BaseJustTrackSdk,
    context: Context,
    logger: Logger,
    val billingVersion: BillingTrackerFactory.BillingVersion,
) : BillingTracker(sdk, context, logger) {
    override fun transformPurchase(purchases: List<Any>): List<ProductPurchase> {
        val result = purchases.map { purchase ->
            val getSkuMethod = purchase.javaClass.getMethod("getProducts")
            val getPurchaseTokenMethod = purchase.javaClass.getMethod("getPurchaseToken")
            val getQuantityMethod = purchase.javaClass.getMethod("getQuantity")
            val getOrderIdMethod = purchase.javaClass.getMethod("getOrderId")
            val productIds =
                getSkuMethod.invoke(purchase).convertToList<String>()

            ProductPurchase(
                productIds ?: listOf(),
                getPurchaseTokenMethod.invoke(purchase) as String,
                getQuantityMethod.invoke(purchase) as Int,
                getOrderIdMethod.invoke(purchase) as String?,
                purchase.toString(),
            )
        }
        return result
    }

    override fun fetchPurchaseDetail(productType: ProductType, purchases: List<ProductPurchase>, consumer: Consumer<List<ProductDetail>?>) {
        val detailListenerClass = Class.forName("com.android.billingclient.api.ProductDetailsResponseListener")
        val listener = Proxy.newProxyInstance(
            detailListenerClass.classLoader,
            arrayOf(detailListenerClass),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any {
                    if (method == null || args == null) return ""
                    if (method.name.equals("onProductDetailsResponse")) {
                        val skuDetailsList =
                            args[1].convertToList<Any>()
                        try {
                            consumer.accept(
                                skuDetailsList?.map {
                                    ProductDetail(
                                        it.javaClass.getMethod("getProductId").invoke(it) as String,
                                        getPurchaseTotalPrice(it, productType),
                                    )
                                },
                            )
                        } catch (exception: java.lang.Exception) {
                            logger.warn(
                                "Unable to obtain product detail: ${billingVersion.name}",
                                exception,
                            )
                        }
                    }
                    return ""
                }
            },
        )

        val billingClientClass = billingClient.javaClass

        try {
            val productQuery = buildProductQueryParams(productType.title, purchases)
            productQuery.let { query ->
                billingClientClass.getMethod(
                    "queryProductDetailsAsync",
                    query.javaClass,
                    detailListenerClass,
                ).invoke(billingClient, productQuery, listener)
            }
        } catch (exception: java.lang.Exception) {
            logger.warn("Failed to fetch IAP purchase details: ${billingVersion.name}", exception)
        }
    }

    @Throws(java.lang.Exception::class)
    private fun getPurchaseTotalPrice(productDetail: Any, productType: ProductType): Money {
        if (productType == ProductType.INAPP) {
            val oneTimeOfferObj = productDetail.javaClass.getMethod("getOneTimePurchaseOfferDetails")
                .invoke(productDetail)
            val priceAmount = oneTimeOfferObj.javaClass.getMethod("getPriceAmountMicros")
                .invoke(oneTimeOfferObj) as Long
            val currencyCode = oneTimeOfferObj.javaClass.getMethod("getPriceCurrencyCode")
                .invoke(oneTimeOfferObj) as String
            return Money(microToDouble(priceAmount), currencyCode)
        } else {
            val subscriptionOffer = productDetail.javaClass.getMethod("getSubscriptionOfferDetails")
                .invoke(productDetail).convertToList<Any>()
                ?: error("No subscription offer for the subscription purchase")
            val firstOffer = subscriptionOffer[0]
            val pricingPhaseWrapper = firstOffer.javaClass.getMethod("getPricingPhases").invoke(firstOffer)
            val pricingPhaseList = pricingPhaseWrapper.javaClass.getMethod("getPricingPhaseList")
                .invoke(pricingPhaseWrapper).convertToList<Any>()
                ?: error("No pricing phase offer for the subscription purchase")

            val pricingPhase = pricingPhaseList[0]

            val priceAmount = pricingPhase.javaClass.getMethod("getPriceAmountMicros")
                .invoke(pricingPhase) as Long
            val currencyCode = pricingPhase.javaClass.getMethod("getPriceCurrencyCode")
                .invoke(pricingPhase) as String
            return Money(microToDouble(priceAmount), currencyCode)
        }
    }

    @Throws(Exception::class)
    private fun buildProductQueryParams(productType: String, purchases: List<ProductPurchase>): Any {
        val productList = ArrayList<Any>()

        purchases.forEach { purchase ->
            purchase.productIds.forEach { productId ->
                val queryClasses = Class.forName("com.android.billingclient.api.QueryProductDetailsParams").classes
                var productClass: Class<*>? = null
                queryClasses.forEach {
                    if (it.name.endsWith("Product")) {
                        productClass = it
                    }
                }

                productClass?.let { product ->
                    var productBuilder = product.getMethod("newBuilder").invoke(productClass)
                    productBuilder = productBuilder.javaClass.getMethod("setProductId", String::class.java)
                        .invoke(productBuilder, productId)
                    productBuilder = productBuilder.javaClass.getMethod("setProductType", String::class.java)
                        .invoke(productBuilder, productType)
                    productBuilder.javaClass.getMethod("build").invoke(productBuilder)?.let {
                        productList.add(it)
                    }
                }
            }
        }

        val skuDetailsParamsClass = Class.forName("com.android.billingclient.api.QueryProductDetailsParams")
        var builder = skuDetailsParamsClass.getMethod("newBuilder")
            .invoke(skuDetailsParamsClass)
        builder = builder.javaClass.getMethod("setProductList", List::class.java)
            .invoke(builder, productList)
        return builder.javaClass.getMethod("build").invoke(builder) as Any
    }

    override suspend fun getPurchaseHistory(skuType: String): List<Pair<String, String>>? = suspendCoroutine { continuation ->
        val billingClientClass = billingClient.javaClass
        val purchaseHistoryResponseListenerClass = Class.forName("com.android.billingclient.api.PurchaseHistoryResponseListener")
        val purchaseHistoryListener = Proxy.newProxyInstance(
            purchaseHistoryResponseListenerClass.classLoader,
            arrayOf(purchaseHistoryResponseListenerClass),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method == null || args == null) {
                        continuation.resume(null)
                        return null
                    }
                    if (method.name.equals("onPurchaseHistoryResponse")) {
                        val purchases =
                            args[1].convertToList<Any>()
                        try {
                            continuation.resume(
                                purchases?.map {
                                    val token =
                                        it.javaClass.getMethod("getPurchaseToken")
                                            .invoke(it)
                                    val json = it.javaClass.getMethod("getOriginalJson").invoke(it)
                                    Pair(token as String, json as String)
                                },
                            )
                        } catch (exception: java.lang.Exception) {
                            logger.warn("Unable to obtain purchase history,V5", exception)
                            continuation.resume(null)
                        }
                        return null
                    }
                    continuation.resume(null)
                    return null
                }
            },
        )

        val paramClass = Class.forName("com.android.billingclient.api.QueryPurchaseHistoryParams")

        var builder = paramClass.getMethod("newBuilder")
            .invoke(paramClass)

        builder = builder.javaClass.getMethod("setProductType", String::class.java)
            .invoke(builder, skuType)
        val param = builder.javaClass.getMethod("build").invoke(builder) as Any

        billingClientClass.getMethod(
            "queryPurchaseHistoryAsync",
            paramClass,
            purchaseHistoryResponseListenerClass,
        ).invoke(billingClient, param, purchaseHistoryListener)
    }
}
