package io.justtrack

import androidx.annotation.VisibleForTesting
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdUnit
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import java.util.concurrent.atomic.AtomicBoolean

internal class IntegrationManager internal constructor(
    private val sdk: BaseJustTrackSdk,
    private val logger: Logger,
) : AutoCloseable {
    @VisibleForTesting val integratedWithAdjoe = AtomicBoolean(false)

    private var onClose: Runnable? = null

    override fun close() {
        onClose?.run()

        onClose = null
    }

    fun integrateWithAdjoe() {
        if (integratedWithAdjoe.getAndSet(true)) {
            return
        }

        val adjoeWave: Class<*> = try {
            try {
                Class.forName("io.adjoe.wave.sdk.AdjoeWave")
            } catch (exception: ClassNotFoundException) {
                Class.forName("io.adjoe.programmatic.sdk.AdjoeProgrammatic")
            }
        } catch (exception: ClassNotFoundException) {
            logger.debug("No AdjoeWave class found, integration disabled")

            return
        }

        try {
            var foundListenerClass: Class<*>
            var impressionClass: Class<*>?
            try {
                foundListenerClass = Class.forName("io.adjoe.wave.sdk.AdjoeImpressionDataListener")
                impressionClass = Class.forName("io.adjoe.wave.sdk.AdjoeAdImpression")
            } catch (exception: ClassNotFoundException) {
                foundListenerClass = Class.forName("io.adjoe.programmatic.sdk.AdjoeImpressionDataListener")
                impressionClass = Class.forName("io.adjoe.programmatic.sdk.AdjoeAdImpression")
            }
            val listenerClass = foundListenerClass
            val listener: Any = ProxyUtils.createProxy(
                listenerClass,
                listenerClass.getMethod("onImpression", impressionClass),
                { args: Array<Any?> ->
                    try {
                        val impressionData = args[0]
                        val impressionDataClass: Class<*> = if (impressionData != null) {
                            impressionData.javaClass
                        } else {
                            logger.warn("integrateWithAdjoe Failed, impressionData is null")

                            return@createProxy null
                        }
                        val getPlacementId = impressionDataClass.getDeclaredMethod("getPlacementId")
                        val getBidderName = impressionDataClass.getDeclaredMethod("getBidderName")
                        val getAppId = impressionDataClass.getDeclaredMethod("getAppId")
                        val getType = impressionDataClass.getDeclaredMethod("getType")

                        val adUnitName = getType.invoke(impressionData) as String?
                        if (adUnitName == null) {
                            logger.warn("AdUnit is null, Adjoe event was invalid")

                            return@createProxy null
                        }
                        val adNetwork = getBidderName.invoke(impressionData) as String
                        val placement = getPlacementId.invoke(impressionData) as String
                        val bundleId = getAppId.invoke(impressionData) as String
                        val result: AsyncFuture<Void> = when (adUnitName) {
                            "BANNER" -> sdk.forwardAdImpression(
                                AdImpression(
                                    unit = AdUnit.Banner,
                                    sdkName = "adjoe",
                                    network = adNetwork,
                                    placement = placement,
                                    bundleId = bundleId,
                                ),
                            )

                            "VIDEO_INTERSTITIAL" -> sdk.forwardAdImpression(
                                AdImpression(
                                    unit = AdUnit.Interstitial,
                                    sdkName = "adjoe",
                                    network = adNetwork,
                                    placement = placement,
                                    bundleId = bundleId,
                                ),
                            )

                            "VIDEO_REWARDED" -> sdk.forwardAdImpression(
                                AdImpression(
                                    unit = AdUnit.Rewarded,
                                    sdkName = "adjoe",
                                    network = adNetwork,
                                    placement = placement,
                                    bundleId = bundleId,
                                ),
                            )

                            else -> {
                                logger.warn("Adjoe event contained invalid ad unit", LoggerFieldsBuilder().with("adUnit", adUnitName))

                                return@createProxy null
                            }
                        }

                        result.registerCallback(
                            object : Callback<Void> {
                                override fun resolve(response: Void) {
                                    // nop
                                }

                                override fun reject(exception: Throwable) {
                                    logger.error("Adjoe event was invalid", exception)
                                }
                            },
                        )
                    } catch (exception: Throwable) {
                        logger.error("Failed to publish adjoe impression event", exception)
                    }
                    null
                },
                logger,
            )

            for (addListener in adjoeWave.methods) {
                // the real method is called something like "addAdImpressionDataListener$programmatic_productionRelease",
                // to make this more resilient, we just iterate all methods and look for one starting
                // with the correct prefix and having the correct type.
                val parameters = addListener.parameterTypes
                if (addListener.name.startsWith("addAdImpressionDataListener") &&
                    parameters.size == 1 && listenerClass == parameters[0]
                ) {
                    addListener.isAccessible = true
                    addListener.invoke(null, listener)

                    return
                }
            }

            logger.error("Failed to find adjoe addAdImpressionDataListener")
        } catch (exception: Throwable) {
            logger.error("Failed to initialize adjoe integration", exception)
        }
    }

    companion object {
        /**
         * Called from Unity when we enable debug logs for the UnityJavaProxy. This will cause a log message
         * to be written for each Java -> C# method call, allowing you to see the last call on the crashing
         * thread.
         */
        @Suppress("unused")
        @JvmStatic
        @JvmName("enableUnityJavaProxyDebugging")
        fun enableUnityJavaProxyDebugging() {
            try {
                val clazz = Class.forName("com.unity3d.player.ReflectionHelper")
                val field = clazz.getDeclaredField("LOG")
                field.isAccessible = true
                field[null] = true
            } catch (exception: ReflectiveOperationException) {
                throw ReflectiveOperationException("Could not set ReflectionHelper.LOG to true", exception)
            }
        }
    }
}
