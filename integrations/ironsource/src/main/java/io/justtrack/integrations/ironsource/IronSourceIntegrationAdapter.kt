package io.justtrack.integrations.ironsource

import android.content.Context
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.impression.LevelPlayImpressionData
import com.unity3d.mediation.impression.LevelPlayImpressionDataListener
import io.justtrack.Callback
import io.justtrack.JustTrackSdk
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdUnit
import io.justtrack.events.Money
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder

class IronSourceIntegrationAdapter : IntegrationAdapter {
    private var logger: Logger? = null
    private var sdk: JustTrackSdk? = null

    private val impressionListener =
        object : LevelPlayImpressionDataListener {
            override fun onImpressionSuccess(impressionData: LevelPlayImpressionData) {
                try {
                    val adUnitName = impressionData.mediationAdUnitName
                    if (adUnitName == null) {
                        logger?.warn("AdUnit is null. Are you testing?")
                        return
                    }

                    val adNetwork = impressionData.adNetwork
                    val placement = impressionData.placement
                    val abTesting = impressionData.ab
                    val segmentName = impressionData.segmentName
                    val instanceName = impressionData.instanceName

                    val revenue = impressionData.revenue ?: 0.0

                    val adUnit =
                        when (adUnitName) {
                            "Banner" -> AdUnit.Banner
                            "Interstitial" -> AdUnit.Interstitial
                            "Rewarded" -> AdUnit.Rewarded
                            else -> {
                                logger?.error(
                                    "Ironsource event contained invalid ad unit $adUnitName",
                                    LoggerFieldsBuilder().with(
                                        "adUnit",
                                        adUnitName,
                                    ),
                                )
                                return
                            }
                        }

                    val adImpression =
                        AdImpression(
                            unit = adUnit,
                            sdkName = "ironsource",
                            network = adNetwork,
                            placement = placement,
                            testGroup = abTesting,
                            segmentName = segmentName,
                            instanceName = instanceName,
                            revenue = Money(revenue, "USD"),
                        )

                    val justtrackSdk = sdk
                    if (justtrackSdk != null) {
                        justtrackSdk.forwardAdImpression(adImpression)
                            .registerCallback(
                                object : Callback<Void?> {
                                    override fun resolve(response: Void?) {
                                        // noop
                                    }

                                    override fun reject(exception: Throwable) {
                                        logger?.error("Ironsource event was invalid", exception)
                                    }
                                },
                            )
                    } else {
                        logger?.error("Failed to publish impression event sdk is null")
                    }
                } catch (exception: Throwable) {
                    logger?.error("Failed to publish impression event", exception)
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
            LevelPlay.addImpressionDataListener(impressionListener)
        } catch (exception: Throwable) {
            logger.error("Failed to initialize IronSource integration", exception)
        }
    }
}
