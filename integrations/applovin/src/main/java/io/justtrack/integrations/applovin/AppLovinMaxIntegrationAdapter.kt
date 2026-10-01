package io.justtrack.integrations.applovin

import android.content.Context
import com.applovin.communicator.AppLovinCommunicator
import com.applovin.communicator.AppLovinCommunicatorMessage
import com.applovin.communicator.AppLovinCommunicatorSubscriber
import com.applovin.sdk.AppLovinSdk
import io.justtrack.Callback
import io.justtrack.JustTrackSdk
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdUnit
import io.justtrack.events.Money
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder

class AppLovinMaxIntegrationAdapter(
    private val userId: String?,
) : IntegrationAdapter {
    private var logger: Logger? = null
    private var sdk: JustTrackSdk? = null

    private val subscriber =
        object : AppLovinCommunicatorSubscriber {
            override fun getCommunicatorId(): String = "justtrack"

            override fun onMessageReceived(message: AppLovinCommunicatorMessage?) {
                try {
                    val data =
                        if (message != null) {
                            message.messageData
                        } else {
                            logger?.error("AppLovin MAX event was null")

                            return
                        }

                    val revenueValue = data.getDouble("revenue")
                    if (revenueValue < 0) {
                        logger?.warn("AppLovin MAX event was invalid (negative revenue)")

                        return
                    }

                    val revenue = Money(revenueValue, "USD")

                    val networkName = data.getString("network_name")
                    val hasNetworkPlacement = data.containsKey("network_placement")
                    var adPlacementId =
                        if (hasNetworkPlacement) {
                            data.getString("network_placement")
                        } else {
                            data.getString("third_party_ad_placement_id")
                        }

                    val adFormatString = data.getString("ad_format")
                    val userSegment = data.getString("user_segment")
                    val adUnitId = data.getString("max_ad_unit_id")

                    var fields = LoggerFieldsBuilder()
                    for (key in data.keySet()) {
                        val value = data[key]
                        if (value == null) {
                            fields = fields.with("bundle_field_$key", "<null>")
                        } else {
                            fields = fields.with("bundle_field_$key", value.toString())
                            if (!hasNetworkPlacement && (key != "third_party_ad_placement_id") &&
                                key.contains(
                                    "placement",
                                ) &&
                                value is String
                            ) {
                                adPlacementId = value
                            }
                        }
                    }
                    logger?.debug("Got AppLovin message bundle", fields)

                    val adUnit: AdUnit =
                        when (adFormatString) {
                            "APP_OPEN", "APPOPEN" -> AdUnit.AppOpen
                            "BANNER" -> AdUnit.Banner
                            "MREC" -> AdUnit.MediumRectangle
                            "INTER" -> AdUnit.Interstitial
                            "REWARDED" -> AdUnit.Rewarded
                            "REWARDED_INTER" -> AdUnit.RewardedInterstitial
                            "NATIVE" -> AdUnit.Native
                            "LEADER" -> AdUnit.Leader
                            else -> {
                                logger?.error(
                                    "AppLovin MAX event contained invalid ad format",
                                    LoggerFieldsBuilder().with(
                                        "adFormat",
                                        adFormatString ?: "<null>",
                                    ),
                                )

                                return
                            }
                        }

                    val result =
                        sdk?.forwardAdImpression(
                            AdImpression(
                                unit = adUnit,
                                sdkName = "appLovin",
                                network = networkName,
                                placement = adPlacementId,
                                segmentName = userSegment,
                                instanceName = adUnitId,
                                revenue = revenue,
                            ),
                        )

                    result?.registerCallback(
                        object : Callback<Void?> {
                            override fun resolve(response: Void?) {
                                // nop
                            }

                            override fun reject(exception: Throwable) {
                                logger?.error("AppLovin MAX event was invalid")
                            }
                        },
                    )
                } catch (exception: Throwable) {
                    logger?.error("Failed to publish revenue message event", exception)
                }
            }
        }

    override fun integrate(
        context: Context,
        sdk: JustTrackSdk,
        logger: Logger,
    ) {
        this.logger = logger
        this.sdk = sdk

        try {
            if (userId != null) {
                AppLovinSdk.getInstance(context)
                    .settings
                    .userIdentifier = userId
            }

            AppLovinCommunicator.getInstance(context).subscribe(subscriber, MAX_REVENUE_EVENTS)

            // report version & integration
            try {
                val version = AppLovinSdk.VERSION

                logger.info(
                    "Completed integration with AppLovin MAX",
                    LoggerFieldsBuilder().with("version", version ?: "null"),
                )
            } catch (exception: Throwable) {
                logger.error(
                    "Completed AppLovin MAX integration, but failed to determine version",
                    exception,
                )
            }
        } catch (exception: Throwable) {
            logger.error("Failed to setup AppLovin MAX integration", exception)
        }
    }

    companion object {
        private const val MAX_REVENUE_EVENTS = "max_revenue_events"
    }
}
