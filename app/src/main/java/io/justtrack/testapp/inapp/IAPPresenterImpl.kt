package io.justtrack.testapp.inapp

import android.app.Activity
import android.app.AlertDialog
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.justtrack.events.Money
import io.justtrack.testapp.MainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IAPPresenterImpl : IAPPresenter {
    private val tag = "MainPresenter"

    private lateinit var billingClient: BillingClient
    private lateinit var context: Activity

    private val consumableList: ArrayList<ProductDetails> = ArrayList()
    private val nonConsumableList: ArrayList<ProductDetails> = ArrayList()
    private val subscriptionList: ArrayList<ProductDetails> = ArrayList()

    private var isAutoIap = false

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            Log.e(tag, "PurchasesUpdatedListener : $billingResult $purchases")
            if (purchases != null) {
                val consumeParams =
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(purchases[0].purchaseToken)
                        .build()
                billingClient.consumeAsync(consumeParams) { _, _ ->
                    Log.e(tag, ": Product Consume")
                }
                purchases.forEach {
                    if (!it.isAcknowledged) {
                        val param =
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(it.purchaseToken).build()
                        billingClient.acknowledgePurchase(
                            param,
                        ) { }
                    }

                    if (!isAutoIap) {
                        val token = it.purchaseToken
                        for (productId in it.products) {
                            val productDetail: ProductDetails = getProductDetails(productId) ?: return@PurchasesUpdatedListener
                            val isSub = subscriptionList.map { it.productId }.contains(productId)

                            val money =
                                if (isSub) {
                                    val subscriptionDetail = productDetail.subscriptionOfferDetails
                                    val pricingInfo = subscriptionDetail!!.get(0).pricingPhases.pricingPhaseList.get(0)
                                    Money(microToDouble(pricingInfo.priceAmountMicros), pricingInfo.priceCurrencyCode)
                                } else {
                                    val purchaseDetail = productDetail.oneTimePurchaseOfferDetails
                                    Money(microToDouble(purchaseDetail!!.priceAmountMicros), purchaseDetail.priceCurrencyCode)
                                }

                            if (isSub) {
                                MainApplication.sdk!!.forwardSubscription(productId, token, money)
                                Log.e(tag, "Manual subscription purchase reported")
                            } else {
                                MainApplication.sdk!!.forwardInApp(productId, token, money)
                                Log.e(tag, "Manual inapp purchase reported")
                            }
                        }
                    }
                }
            }
        }

    override fun start(
        context: Activity,
        isAutoIap: Boolean,
    ) {
        this.context = context
        this.isAutoIap = isAutoIap
        billingClient =
            BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build()

        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    try {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.e(tag, "onBillingSetupFinished: Billing connect OK")
                            queryProductInApp()
                            queryProductSub()
                        } else {
                            Log.e(tag, "onBillingSetupFinished: Billing NOT OK")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "onBillingSetupFinished: " + e.message)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Toast.makeText(
                        context,
                        "Billing Disconnected",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    private fun microToDouble(micro: Long?): Double {
        if (micro == null) return 0.0
        return micro / 1000000.0
    }

    private fun getProductDetails(productId: String): ProductDetails? {
        for (sub in subscriptionList) {
            if (productId == sub.productId) {
                return sub
            }
        }

        for (product in consumableList) {
            if (productId == product.productId) {
                return product
            }
        }

        for (product in nonConsumableList) {
            if (productId == product.productId) {
                return product
            }
        }

        return null
    }

    fun queryProductInApp() {
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder().apply {
                            setProductId("product_1")
                            setProductType(BillingClient.ProductType.INAPP)
                        }.build(),
                        QueryProductDetailsParams.Product.newBuilder().apply {
                            setProductId("product_2")
                            setProductType(BillingClient.ProductType.INAPP)
                        }.build(),
                        QueryProductDetailsParams.Product.newBuilder().apply {
                            setProductId("product_3")
                            setProductType(BillingClient.ProductType.INAPP)
                        }.build(),
                    ),
                )
                .build()
        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { _, productDetailsList ->
            Log.e(tag, "queryProduct: Query Product: Completed ${productDetailsList.productDetailsList.size}")
            for (productDetail in productDetailsList.productDetailsList) {
                if (productDetail.productType == BillingClient.ProductType.INAPP) {
                    consumableList.add(productDetail)
                } else {
                    subscriptionList.add(productDetail)
                }
            }
        }
    }

    fun queryProductSub() {
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder().apply {
                            setProductId("sub_1")
                            setProductType(BillingClient.ProductType.SUBS)
                        }.build(),
                    ),
                )
                .build()
        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { _, productDetailsList ->
            Log.e(tag, "queryProduct: Query Product: Completed" + productDetailsList.productDetailsList.size)
            for (productDetail in productDetailsList.productDetailsList) {
                if (productDetail.productType == BillingClient.ProductType.INAPP) {
                    consumableList.add(productDetail)
                } else {
                    subscriptionList.add(productDetail)
                }
            }
        }
    }

    private fun purchase(item: ProductDetails) =
        CoroutineScope(Dispatchers.IO).launch {
            val productDetailsParamsList =
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                        setProductDetails(item)

                        if (item.productType == BillingClient.ProductType.SUBS) {
                            setOfferToken(item.subscriptionOfferDetails!![0].offerToken)
                        }
                    }.build(),
                )
            val accountId = MainApplication.sdk!!.installInstanceId.await()
            val billingFlowParams =
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .setObfuscatedAccountId(accountId)
                    .build()

            billingClient.launchBillingFlow(context, billingFlowParams)
        }

    override fun consumeAllProduct() {
        val param =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        billingClient.queryPurchasesAsync(
            param,
        ) { _, purchaseList ->
            purchaseList.asIterable().forEach {
                val consumeParams =
                    ConsumeParams.newBuilder()
                        .setPurchaseToken(it.purchaseToken)
                        .build()
                billingClient.consumeAsync(consumeParams) { _, _ ->
                    Log.e(tag, ": Product Consume")
                }
            }
        }
    }

    override fun displaySelectionDialogConsumable() {
        displaySelectionDialog(consumableList)
    }

    override fun displaySelectionDialogNonConsumable() {
        displaySelectionDialog(nonConsumableList)
    }

    override fun displaySelectionDialogSubs() {
        displaySelectionDialog(subscriptionList)
    }

    private fun displaySelectionDialog(items: List<ProductDetails>) {
        AlertDialog.Builder(this.context).apply {
            setTitle("Select Item to Purchase")
            setSingleChoiceItems(
                items.map { it.name }.toTypedArray(),
                -1,
            ) { dialogInterface, i ->
                purchase(items[i])
                dialogInterface.dismiss()
            }

            setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.cancel()
            }
        }.show()
    }
}
