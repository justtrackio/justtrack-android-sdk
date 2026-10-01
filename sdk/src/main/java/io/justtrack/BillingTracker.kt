package io.justtrack

import android.content.Context
import androidx.core.util.Consumer
import io.justtrack.events.Money
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.util.ExcludeFromJacocoGeneratedReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

@ExcludeFromJacocoGeneratedReport
internal abstract class BillingTracker(
    private val sdk: JustTrackSdkImpl,
    private val context: Context,
    protected val logger: Logger,
    protected val billingVersion: BillingTrackerFactory.BillingVersion,
) {
    protected lateinit var billingClient: Any
    private val isTracking = AtomicBoolean(false)

    init {
        initializeBillingClient()
    }

    private companion object {
        private const val PURCHASE_SERVICE_TIMEOUT = -3
        private const val PURCHASE_FEATURE_NOT_SUPPORTED = -2
        private const val PURCHASE_SERVICE_DISCONNECTED = -1
        private const val PURCHASE_OK = 0
        private const val PURCHASE_USER_CANCELED = 1
        private const val PURCHASE_SERVICE_UNAVAILABLE = 2
        private const val PURCHASE_BILLING_UNAVAILABLE = 3
        private const val PURCHASE_ITEM_UNAVAILABLE = 4
        private const val PURCHASE_DEVELOPER_ERROR = 5
        private const val PURCHASE_ERROR = 6
        private const val PURCHASE_ITEM_ALREADY_OWNED = 7
        private const val PURCHASE_ITEM_NOT_OWNED = 8
        private const val MICROS_PER_CURRENCY_UNIT = 1_000_000.0
    }

    /**
     * Enabling the BillingTracker to track the IAP. By default istracking is false
     * @param enabled
     */
    fun setEnable(enabled: Boolean) {
        isTracking.set(enabled)
    }

    /**
     * transform BillingClient Purchase class into ProductPurchase to eliminate some require field
     * that are SDK version dependent.
     */
    protected abstract fun transformPurchase(purchases: List<Any>): List<ProductPurchase>

    /**
     * Class "Purchase" that is return from the Billing SDK
     * does not have the information for pricing and currency.
     * this require the use of "queryProductDetailsAsync" method to obtain such data.
     * Since we also do not know the productType of the purchase whether it is "SUBS" or "INAPP"
     * We have to add the purchase productID in both query for both productDetail of
     * Subs and INAPP
     */
    protected abstract fun fetchPurchaseDetail(productType: ProductType, purchases: List<ProductPurchase>, consumer: Consumer<List<ProductDetail>?>)

    /**
     * Retrieve a list of previous purchases, filter by a given sku-type or product-type.
     * return a list of a pair containing product_token and originalJson.
     */
    protected abstract suspend fun getPurchaseHistory(skuType: String): List<Pair<String, String>>?

    protected fun microToDouble(micro: Long?): Double {
        if (micro == null) return 0.0
        return micro / MICROS_PER_CURRENCY_UNIT
    }

    protected inline fun <reified T> Any?.convertToList(): List<T>? {
        if (this is List<*>) {
            return this.filterIsInstance<T>().takeIf { it.size == this.size }
        }
        return null
    }

    /**
     * initializing the BillingClient and initializing the purchase update listener
     */
    private fun initializeBillingClient() {
        try {
            val billingClientClass = Class.forName("com.android.billingclient.api.BillingClient")
            var billingClientBuilder =
                billingClientClass.getMethod("newBuilder", Context::class.java)
                    .invoke(billingClientClass, context)
                    ?: error("BillingClient.newBuilder returned null")

            val purchaseListenerClass = Class.forName("com.android.billingclient.api.PurchasesUpdatedListener")
            val purchaseListener = createPurchaseListenerInstance(purchaseListenerClass)

            val setPurchaseListenerMethod = billingClientBuilder.javaClass.getMethod("setListener", purchaseListenerClass)
            billingClientBuilder =
                setPurchaseListenerMethod.invoke(billingClientBuilder, purchaseListener)
                    ?: error("BillingClient.Builder.setListener returned null")

            billingClientBuilder = enablePendingPurchases(billingClientBuilder)

            val buildMethod = billingClientBuilder.javaClass.getMethod("build")
            val billingClient = buildMethod.invoke(billingClientBuilder)

            if (billingClient != null) {
                this.billingClient = billingClient
            }

            val billingStartListenerClass = Class.forName("com.android.billingclient.api.BillingClientStateListener")

            val billingStartListener = createBillingStartListenerInstance(billingStartListenerClass)

            val setBillingStartMethod = billingClientClass.getMethod("startConnection", billingStartListenerClass)

            setBillingStartMethod.invoke(this.billingClient, billingStartListener)
        } catch (exception: Exception) {
            logger.warn("Failed to initialize BillingTracker", exception)
        }
    }

    private fun enablePendingPurchases(billingClientBuilder: Any): Any {
        if (billingVersion != BillingTrackerFactory.BillingVersion.VERSION_8) {
            return billingClientBuilder.javaClass.getMethod("enablePendingPurchases")
                .invoke(billingClientBuilder)
                ?: error("BillingClient.Builder.enablePendingPurchases returned null")
        }

        val pendingPurchasesParamsClass =
            Class.forName("com.android.billingclient.api.PendingPurchasesParams")
        var pendingPurchasesParamsBuilder =
            pendingPurchasesParamsClass.getMethod("newBuilder").invoke(pendingPurchasesParamsClass)
                ?: error("PendingPurchasesParams.newBuilder returned null")
        pendingPurchasesParamsBuilder =
            pendingPurchasesParamsBuilder.javaClass.getMethod("enableOneTimeProducts")
                .invoke(pendingPurchasesParamsBuilder)
                ?: error("PendingPurchasesParams.Builder.enableOneTimeProducts returned null")
        val pendingPurchasesParams =
            pendingPurchasesParamsBuilder.javaClass.getMethod("build")
                .invoke(pendingPurchasesParamsBuilder)
                ?: error("PendingPurchasesParams.Builder.build returned null")

        return billingClientBuilder.javaClass.getMethod(
            "enablePendingPurchases",
            pendingPurchasesParamsClass,
        ).invoke(billingClientBuilder, pendingPurchasesParams)
            ?: error("BillingClient.Builder.enablePendingPurchases returned null")
    }

    @Throws(Exception::class)
    private fun createPurchaseListenerInstance(purchaseListener: Class<*>): Any {
        val implementsClasses = arrayOf(purchaseListener)
        val listener = Proxy.newProxyInstance(
            purchaseListener.classLoader,
            implementsClasses,
            @ExcludeFromJacocoGeneratedReport
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method == null) {
                        return null
                    }

                    val declaringClass = method.declaringClass
                    if (declaringClass.equals(purchaseListener) && method.name.equals("onPurchasesUpdated")) {
                        try {
                            val billingResultArg = args?.get(0)
                            val purchasesArg = args?.get(1)

                            if (billingResultArg == null) {
                                error("Failed to retrieve purchase information, BillingResult is null")
                            }

                            val billingResultGetCodeMethod =
                                billingResultArg.javaClass.getMethod("getResponseCode")
                            val billingResultCode = billingResultGetCodeMethod.invoke(billingResultArg)

                            when (billingResultCode) {
                                PURCHASE_SERVICE_TIMEOUT -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_SERVICE_TIMEOUT")
                                    return null
                                }

                                PURCHASE_FEATURE_NOT_SUPPORTED -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_FEATURE_NOT_SUPPORTED")
                                    return null
                                }

                                PURCHASE_SERVICE_DISCONNECTED -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_SERVICE_DISCONNECTED")
                                    return null
                                }

                                PURCHASE_OK -> {
                                    // fallthrough
                                }

                                PURCHASE_USER_CANCELED -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_USER_CANCELED")
                                    return null
                                }

                                PURCHASE_SERVICE_UNAVAILABLE -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_SERVICE_UNAVAILABLE")
                                    return null
                                }

                                PURCHASE_BILLING_UNAVAILABLE -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_BILLING_UNAVAILABLE")
                                    return null
                                }

                                PURCHASE_ITEM_UNAVAILABLE -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_ITEM_UNAVAILABLE")
                                    return null
                                }

                                PURCHASE_DEVELOPER_ERROR -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_DEVELOPER_ERROR")
                                    return null
                                }

                                PURCHASE_ERROR -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_ERROR")
                                    return null
                                }

                                PURCHASE_ITEM_ALREADY_OWNED -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_ITEM_ALREADY_OWNED")
                                    return null
                                }

                                PURCHASE_ITEM_NOT_OWNED -> {
                                    logger.debug("Ignoring purchase update, got response code PURCHASE_ITEM_NOT_OWNED")
                                    return null
                                }

                                else -> {
                                    logger.debug("Ignoring purchase update, got unknown response code $billingResultCode")
                                    return null
                                }
                            }

                            if (purchasesArg == null) {
                                error("Failed to retrieve purchase information, Purchases is null")
                            }

                            val purchases = purchasesArg.convertToList<Any>()

                            if (isTracking.get() && !purchases.isNullOrEmpty()) {
                                reportTransaction(transformPurchase(purchases))
                            }
                            return null
                        } catch (exception: java.lang.Exception) {
                            logger.warn("Failed to retrieve billing purchase data", exception)
                            return null
                        }
                    } else {
                        if (declaringClass.isInterface) {
                            logger.warn(
                                "Failed to handle class to proxied interface method",
                                LoggerFieldsBuilder()
                                    .with("declaringClass", declaringClass.name)
                                    .with("methodName", method.name)
                                    .with("returnType", method.returnType.name),
                            )
                            return null
                        }
                        return if (args != null) {
                            method.invoke(this, args)
                        } else {
                            method.invoke(this)
                        }
                    }
                }
            },
        )
        return listener
    }

    @Throws(Exception::class)
    private fun createBillingStartListenerInstance(startListenerClass: Class<*>): Any {
        val implementsClasses = arrayOf(startListenerClass)
        return Proxy.newProxyInstance(
            startListenerClass.classLoader,
            implementsClasses,
            @ExcludeFromJacocoGeneratedReport
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method == null) return null

                    val declaringClass = method.declaringClass
                    if (declaringClass.equals(startListenerClass) &&
                        (method.name.equals("onBillingSetupFinished") || method.name.equals("onBillingServiceDisconnected"))
                    ) {
                        return ""
                    } else {
                        if (declaringClass.isInterface) {
                            logger.warn(
                                "Failed to handle class to proxied interface method",
                                LoggerFieldsBuilder()
                                    .with("declaringClass", declaringClass.name)
                                    .with("methodName", method.name)
                                    .with("returnType", method.returnType.name),
                            )
                            return null
                        }
                        return if (args != null) {
                            method.invoke(this, args)
                        } else {
                            method.invoke(this)
                        }
                    }
                }
            },
        )
    }

    /**
     * Class "Purchases" that is return from the Billing SDK
     * does not have the information for pricing and currency.
     * this require the use of "queryProductDetailsAsync" method to obtain such data.
     * Since we also do not know the productType of the purchase whether it is "SUBS" or "INAPP"
     * We have to add the purchase productID in both query for both productDetail of
     * Subs and INAPP
     */
    private fun reportTransaction(purchases: List<ProductPurchase>) {
        val productDetailInAppConsumer = Consumer<List<ProductDetail>?> {
            it?.let { productDetails ->
                reportTransaction(ProductType.INAPP, productDetails, purchases)
            }
        }

        val productDetailSubsConsumer = Consumer<List<ProductDetail>?> {
            it?.let { productDetails ->
                reportTransaction(ProductType.SUBS, productDetails, purchases)
            }
        }

        fetchPurchaseDetail(ProductType.INAPP, purchases, productDetailInAppConsumer)
        fetchPurchaseDetail(ProductType.SUBS, purchases, productDetailSubsConsumer)
    }

    private fun reportTransaction(productType: ProductType, productDetails: List<ProductDetail>, purchases: List<ProductPurchase>) {
        productDetails.let { details ->
            val productDetailsMap = details.associateBy { it.productId }

            purchases.forEach { purchase ->
                purchase.productIds.forEach { purchaseProduct ->
                    val productDetail = productDetailsMap[purchaseProduct]
                    if (productDetail != null) {
                        reportTransaction(
                            productType,
                            productDetail.productId,
                            purchase.purchaseToken,
                            multiplyMoneyByQuantity(productDetail.money, purchase.quantity),
                        )
                        logTransaction(productType, purchase, productDetail)
                    }
                }
            }
        }
    }

    private fun logTransaction(productType: ProductType, purchase: ProductPurchase, productDetails: ProductDetail) {
        CoroutineScope(Dispatchers.IO).launch @ExcludeFromJacocoGeneratedReport {
            val purchaseHistory = checkPurchaseHistory(purchase.purchaseToken)
            val billingVersion = BillingTrackerFactory.getBillingClientVersion()
            val log = LoggerFieldsBuilder()
                .with("billing_version", billingVersion.name)
                .with("product_type", productType.title)
                .with("purchase", purchase.toString())
                .with("product_detail", productDetails.toString())
                .with("orderId", purchase.orderId ?: "")
                .with("history", purchaseHistory.toString())

            val orderId = purchase.orderId

            logger.debug(
                buildString {
                    append("purchase_product")
                    if (orderId != null) {
                        if (!orderId.contains("GPA")) {
                            append(" fraud")
                        }
                    } else {
                        append(" missing orderId")
                    }
                },
                log,
            )
        }
    }

    private suspend fun checkPurchaseHistory(purchaseToken: String): String? {
        getPurchaseHistory(ProductType.INAPP.title)?.let {
            it.forEach { pair ->
                if (purchaseToken == pair.first) {
                    return pair.second
                }
            }
        }

        getPurchaseHistory(ProductType.SUBS.title)?.let {
            it.forEach { pair ->
                if (purchaseToken == pair.first) {
                    return pair.second
                }
            }
        }
        return null
    }

    private fun multiplyMoneyByQuantity(moneyPerUnit: Money, quantity: Int): Money {
        return Money(
            moneyPerUnit.value * quantity,
            moneyPerUnit.currency,
        )
    }

    private fun reportTransaction(productType: ProductType, productId: String, productToken: String, money: Money) {
        if (productType == ProductType.INAPP) {
            sdk.forwardInApp(
                productId,
                productToken,
                money,
            )
        } else {
            sdk.forwardSubscription(
                productId,
                productToken,
                money,
            )
        }
    }
}
