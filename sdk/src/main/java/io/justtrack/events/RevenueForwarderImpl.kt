package io.justtrack.events

import io.justtrack.AppEvent
import io.justtrack.AsyncFuture
import io.justtrack.ErrorFuture
import io.justtrack.EventTracker
import io.justtrack.ProductType
import io.justtrack.SessionManager
import io.justtrack.ads.AdImpression
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

internal class RevenueForwarderImpl(
    private val eventTracker: EventTracker,
    private val sessionManager: SessionManager,
    private val isTracking: AtomicBoolean,
    private val logger: Logger,
) : RevenueForwarder {
    override fun forwardAdImpression(adImpression: AdImpression): AsyncFuture<Void?> {
        return when {
            !isTracking.get() -> {
                ErrorFuture(SdkNotTrackingException())
            }
            adImpression.revenue != null && adImpression.revenue!!.value < 0 -> {
                val exception = InvalidFieldException("Negative revenue for AdFormat")
                logger.warn(
                    "Negative revenue for AdFormat",
                    LoggerFieldsBuilder()
                        .with("adUnit", adImpression.unit)
                        .with("revenue", adImpression.revenue!!.value)
                        .with("currency", adImpression.revenue!!.currency),
                )
                ErrorFuture(exception)
            }
            adImpression.unit.isEmpty() -> {
                val exception = InvalidFieldException("Empty AdUnit")

                logger.warn("Empty AdUnit")

                ErrorFuture(exception)
            }
            else -> {
                handleProcessedAdImpression(adImpression)
            }
        }
    }

    private fun handleProcessedAdImpression(adImpression: AdImpression): AsyncFuture<Void?> {
        val event: AppEvent = JtAdInternalEvent(
            "success",
            if (adImpression.bundleId != null) adImpression.bundleId else "",
            if (adImpression.instanceName != null) adImpression.instanceName else "",
            if (adImpression.network != null) adImpression.network else "",
            if (adImpression.placement != null) adImpression.placement else "",
            adImpression.sdkName,
            if (adImpression.segmentName != null) adImpression.segmentName else "",
            adImpression.unit,
            if (adImpression.testGroup != null) adImpression.testGroup else "",
            adImpression.revenue,
            Date(),
        )

        val impressionState = adImpression.state
        if (impressionState != null) {
            event.addDimension(
                "jt_impression_state",
                impressionState
                    .encodedName,
            )
        }

        if (adImpression.revenue != null) {
            event.setValue(adImpression.revenue!!)
        } else {
            event.setValue(Money(0.0, "USD"))
        }

        try {
            event.validate()
        } catch (exception: InvalidFieldException) {
            logger.warn(
                "Not publishing invalid ad impression",
                LoggerFieldsBuilder()
                    .with("adUnit", adImpression.unit)
                    .with("exception", exception),
            )

            return ErrorFuture(exception)
        }

        return eventTracker.track(event, sessionManager)
    }

    override fun forwardInApp(productId: String, token: String, money: Money): Boolean {
        return forwardPurchase(ProductType.INAPP, productId, token, "purchase", money)
    }

    override fun forwardSubscription(subscriptionId: String, token: String, money: Money): Boolean {
        return forwardPurchase(ProductType.SUBS, subscriptionId, token, "subscription", money)
    }

    private fun forwardPurchase(productType: ProductType, id: String, token: String, jtProductType: String, money: Money): Boolean {
        if (money.value < 0) {
            logger.warn(
                "Negative revenue for ${productType.purchaseTitle} purchase",
                LoggerFieldsBuilder()
                    .with("${productType.title}Id", id)
                    .with("revenue", money.value)
                    .with("currency", money.currency),
            )

            return false
        }
        return forwardTransaction(productType, id, token, jtProductType, money)
    }

    private fun forwardTransaction(productType: ProductType, id: String, token: String, jtProductType: String, money: Money): Boolean {
        try {
            money.validate()
        } catch (exception: InvalidFieldException) {
            logInvalidFieldException(exception, productType, id)
            return false
        }

        eventTracker.track(
            JtPurchaseInternalEvent(
                "success",
                id,
                token,
                jtProductType,
                money,
                Date(),
            ),
            sessionManager,
        )

        return true
    }

    private fun logInvalidFieldException(exception: InvalidFieldException, productType: ProductType, id: String) {
        logger.warn(
            "Not publishing invalid ${productType.title} purchase",
            LoggerFieldsBuilder()
                .with("${productType.title}Id", id)
                .with("exception", exception),
        )
    }

    private val ProductType.purchaseTitle: String
        get() = when (this) {
            ProductType.INAPP -> "product"
            ProductType.SUBS -> "subscription"
        }
}
