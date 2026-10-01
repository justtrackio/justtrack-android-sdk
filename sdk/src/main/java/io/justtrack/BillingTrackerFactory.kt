package io.justtrack

import android.content.Context
import io.justtrack.log.Logger
import io.justtrack.util.ExcludeFromJacocoGeneratedReport

@ExcludeFromJacocoGeneratedReport
internal object BillingTrackerFactory {
    /**
     * Versions 5 through 7 share the same APIs used by the tracker. Version 8 introduced breaking
     * changes and uses a dedicated implementation.
     */
    @JvmStatic
    fun getBillingClient(sdk: JustTrackSdkImpl, context: Context, logger: Logger): BillingTracker? {
        if (!hasBillingSDK()) return null
        val billingVersion = getBillingClientVersion()
        return when (billingVersion) {
            BillingVersion.VERSION_8 -> BillingTrackerEight(sdk, context, logger)
            else -> BillingTrackerFive(sdk, context, logger, billingVersion)
        }
    }

    /**
     * In case there is version 6, this will return BillingTrackerFive. Which some method will be
     * deprecated but still usable.
     */
    internal fun getBillingClientVersion(): BillingVersion {
        val billingClientClass = Class.forName("com.android.billingclient.BuildConfig")
        val versionString =
            billingClientClass.getField("VERSION_NAME").get(billingClientClass) as String
        return getBillingClientVersion(versionString)
    }

    internal fun getBillingClientVersion(versionString: String): BillingVersion {
        return when {
            versionString.startsWith("5.") -> BillingVersion.VERSION_5
            versionString.startsWith("6.") -> BillingVersion.VERSION_6
            versionString.startsWith("7.") -> BillingVersion.VERSION_7
            else -> BillingVersion.VERSION_8
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

    @ExcludeFromJacocoGeneratedReport
    internal enum class BillingVersion {
        VERSION_5,
        VERSION_6,
        VERSION_7,
        VERSION_8,
    }
}
