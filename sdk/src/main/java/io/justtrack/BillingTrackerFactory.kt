package io.justtrack

import android.content.Context
import io.justtrack.log.Logger

internal object BillingTrackerFactory {
    /**
     * Version 5 & 6 API remain unchanged so both version will be using BillingTrackerFive.
     */
    @JvmStatic
    fun getBillingClient(sdk: BaseJustTrackSdk, context: Context, logger: Logger): BillingTracker? {
        if (!hasBillingSDK()) return null
        val billingVersion = getBillingClientVersion()
        return BillingTrackerFive(sdk, context, logger, billingVersion)
    }

    /**
     * In case there is version 6, this will return BillingTrackerFive. Which some method will be
     * deprecated but still usable.
     */
    internal fun getBillingClientVersion(): BillingVersion {
        val billingClientClass = Class.forName("com.android.billingclient.BuildConfig")
        val versionString =
            billingClientClass.getField("VERSION_NAME").get(billingClientClass) as String
        return if (versionString.startsWith("5.")) {
            BillingVersion.VERSION_5
        } else if (versionString.startsWith("6.")) {
            BillingVersion.VERSION_6
        } else {
            BillingVersion.VERSION_7
        }
    }

    private fun hasBillingSDK(): Boolean {
        return try {
            Class.forName("com.android.billingclient.api.BillingClient")
            true
        } catch (e: Exception) {
            false
        }
    }

    internal enum class BillingVersion {
        VERSION_5,
        VERSION_6,
        VERSION_7,
    }
}
