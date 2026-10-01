package io.justtrack.integrations.unityads

import android.content.Context
import com.unity3d.services.core.properties.ClientProperties
import io.justtrack.JustTrackSdk
import io.justtrack.ads.AdImpression
import io.justtrack.ads.AdImpressionState
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.description.NamedElement
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.MethodCall
import net.bytebuddy.matcher.ElementMatchers
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

class UnityAdsIntegrationAdapter : IntegrationAdapter {
    override fun integrate(
        context: Context,
        sdk: JustTrackSdk,
        logger: Logger,
    ) {
        // Log Unity Ads version at the start
        try {
            val unityAdsClass = Class.forName("com.unity3d.ads.UnityAds")
            val getVersionMethod = unityAdsClass.getDeclaredMethod("getVersion")
            val version = getVersionMethod.invoke(null) as String?
            logger.info(
                "Starting Unity Ads integration",
                LoggerFieldsBuilder().with("unityAdsVersion", version ?: "unknown"),
            )
        } catch (exception: Exception) {
            logger.error("Failed to detect Unity Ads version before integration", exception)
        }

        try {
            // prepare the case where UnityAds hasn't been initialized yet
            try {
                if (ClientProperties.getApplicationContext() == null) {
                    ClientProperties.setApplicationContext(context.applicationContext)
                }
            } catch (exception: Exception) {
                logger.error("Failed to prepare Unity integration - ClientProperties error", exception)
            }

            // banner ads
            val bannerViewCacheClass =
                try {
                    Class.forName("com.unity3d.services.banners.BannerViewCache")
                } catch (e: ClassNotFoundException) {
                    logger.error(
                        "BannerViewCache class not found - Unity banner integration failed",
                        e,
                    )
                    throw e
                }
            BannerViewCacheAdapter.bannerViewCacheClass = bannerViewCacheClass
            // this needs to be its own weak reference, we don't want to start reporting sessions
            // for other sdk instances if the sdk is restarted while we are still running
            BannerViewCacheAdapter.sdk = WeakReference(sdk)
            BannerViewCacheAdapter.logger = WeakReference(logger)

            val bannerViewCacheInstance =
                try {
                    val getInstanceMethod = bannerViewCacheClass.getDeclaredMethod("getInstance")
                    val instance = getInstanceMethod.invoke(null)
                    instance
                } catch (e: NoSuchMethodException) {
                    logger.error(
                        "BannerViewCache.getInstance() method not found - Unity banner integration failed",
                        e,
                    )
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to invoke BannerViewCache.getInstance()", e)
                    throw e
                }

            try {
                ByteBuddy()
                    .subclass(bannerViewCacheClass, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                    .name("JustTrackBannerViewCacheWrapper")
                    .defineConstructor(Visibility.PUBLIC)
                    .intercept(MethodCall.invoke(bannerViewCacheClass.getDeclaredConstructor()))
                    .defineMethod("justtrackInit", Void.TYPE, Modifier.PUBLIC)
                    .withParameters(bannerViewCacheClass)
                    .intercept(
                        MethodCall.invoke(
                            BannerViewCacheAdapter::class.java.getMethod(
                                "init",
                                Any::class.java,
                                Any::class.java,
                            ),
                        ).withThis().withAllArguments(),
                    )
                    .method(
                        ElementMatchers.named<NamedElement>("triggerBannerLoadEvent").and(
                            ElementMatchers.takesArguments(
                                String::class.java,
                            ),
                        ),
                    )
                    .intercept(
                        MethodCall.invoke(
                            BannerViewCacheAdapter::class.java.getMethod(
                                "triggerBannerLoadEvent",
                                String::class.java,
                            ),
                        ).withArgument(0),
                    )
                    .make().use { classCode ->
                        val wrappedBannerViewCacheClass =
                            classCode.load(
                                bannerViewCacheClass.classLoader,
                                AndroidClassLoadingStrategy.Wrapping(context.codeCacheDir),
                            ).loaded

                        val newBannerViewInstance = wrappedBannerViewCacheClass.newInstance()
                        wrappedBannerViewCacheClass.getMethod("justtrackInit", bannerViewCacheClass)
                            .invoke(newBannerViewInstance, bannerViewCacheInstance)

                        val instanceField = bannerViewCacheClass.getDeclaredField("instance")
                        instanceField.isAccessible = true
                        instanceField[null] = newBannerViewInstance
                        instanceField.isAccessible = false
                    }
            } catch (e: NoSuchMethodException) {
                logger.error(
                    "Failed to create BannerViewCache wrapper - method not found",
                    e,
                    LoggerFieldsBuilder().with("error", e.message ?: "unknown"),
                )
                throw e
            } catch (e: NoSuchFieldException) {
                logger.error(
                    "Failed to replace BannerViewCache instance - field not found",
                    e,
                    LoggerFieldsBuilder().with("error", e.message ?: "unknown"),
                )
                throw e
            } catch (e: Exception) {
                logger.error("Failed to wrap BannerViewCache", e)
                throw e
            }

            // interstitial and rewarded ads
            val showModuleClass =
                try {
                    Class.forName("com.unity3d.services.ads.operation.show.ShowModule")
                } catch (e: ClassNotFoundException) {
                    logger.error(
                        "ShowModule class not found - Unity interstitial/rewarded integration failed",
                        e,
                    )
                    throw e
                }

            val showInstance =
                try {
                    showModuleClass.getDeclaredMethod("getInstance").invoke(null)
                } catch (e: NoSuchMethodException) {
                    logger.error(
                        "ShowModule.getInstance() method not found",
                        e,
                    )
                    throw e
                }

            val showModuleInterface =
                try {
                    Class.forName("com.unity3d.services.ads.operation.show.IShowModule")
                } catch (e: ClassNotFoundException) {
                    logger.error(
                        "IShowModule interface not found",
                        e,
                    )
                    throw e
                }

            val getMethod = showModuleInterface.getMethod("get", String::class.java)
            val completionStateClass =
                try {
                    Class.forName("com.unity3d.ads.UnityAds\$UnityAdsShowCompletionState") as Class<Enum<*>>
                } catch (e: ClassNotFoundException) {
                    logger.error(
                        "UnityAdsShowCompletionState enum not found",
                        e,
                    )
                    throw e
                }
            val stateConstants = completionStateClass.enumConstants
            if (stateConstants == null) {
                logger.error(
                    "UnityAdsShowCompletionState is not an enum or has no constants",
                    LoggerFieldsBuilder().with("className", completionStateClass.canonicalName ?: "unknown"),
                )

                return
            }

            var completedEnumFound: Enum<*>? = null
            for (stateConstant in stateConstants) {
                if (stateConstant.name == "COMPLETED") {
                    completedEnumFound = stateConstant
                    break
                }
            }
            if (completedEnumFound == null) {
                val availableConstants = stateConstants.joinToString(", ") { it.name }
                logger.error(
                    "UnityAdsShowCompletionState does not contain COMPLETED enum",
                    LoggerFieldsBuilder()
                        .with("className", completionStateClass.canonicalName ?: "unknown")
                        .with("availableConstants", availableConstants),
                )

                return
            }
            val completedEnum: Enum<*> = completedEnumFound

            val showOperationClass =
                try {
                    Class.forName("com.unity3d.services.ads.operation.show.ShowOperation")
                } catch (e: ClassNotFoundException) {
                    logger.error(
                        "ShowOperation class not found",
                        e,
                    )
                    throw e
                }

            val getShowOperationStateMethod =
                try {
                    showOperationClass.getMethod("getShowOperationState")
                } catch (e: NoSuchMethodException) {
                    logger.error(
                        "ShowOperation.getShowOperationState() method not found",
                        e,
                    )
                    throw e
                }

            val showOperationStateClass =
                try {
                    Class.forName("com.unity3d.services.ads.operation.show.ShowOperationState")
                } catch (e: ClassNotFoundException) {
                    logger.error(
                        "ShowOperationState class not found",
                        e,
                    )
                    throw e
                }

            val placementIdField =
                try {
                    showOperationStateClass.getField("placementId")
                } catch (e: NoSuchFieldException) {
                    logger.error(
                        "ShowOperationState.placementId field not found",
                        e,
                    )
                    throw e
                }

            val newShowInstance =
                ProxyUtils.createProxy(
                    showModuleInterface,
                    showInstance,
                    showModuleInterface.getMethod(
                        "onUnityAdsShowComplete",
                        String::class.java,
                        completionStateClass,
                    ),
                    { args: Array<Any?> ->
                        try {
                            val id = args[0] as String?
                            val isComplete = args[1] === completedEnum
                            val showOperation = getMethod.invoke(showInstance, id)
                            val showOperationState = getShowOperationStateMethod.invoke(showOperation)
                            val placementId = placementIdField[showOperationState] as String?

                            logger.info(
                                "Unity ad show completed",
                                LoggerFieldsBuilder()
                                    .with("placementId", placementId ?: "null")
                                    .with("isComplete", isComplete.toString()),
                            )

                            sdk.forwardAdImpression(
                                AdImpression(
                                    unit = "impression",
                                    sdkName = "unity",
                                    network = "unity",
                                    placement = placementId,
                                    state =
                                        if (isComplete) {
                                            AdImpressionState.COMPLETED
                                        } else {
                                            AdImpressionState.SKIPPED
                                        },
                                ),
                            )
                        } catch (exception: Throwable) {
                            logger.error("Failed to handle Unity ad show event", exception)
                        }
                        null
                    },
                    logger,
                )

            val instanceField =
                try {
                    showModuleClass.getDeclaredField("instance")
                } catch (e: NoSuchFieldException) {
                    logger.error(
                        "ShowModule.instance field not found",
                        e,
                    )
                    throw e
                }
            instanceField.isAccessible = true
            instanceField[null] = newShowInstance
            instanceField.isAccessible = false
            // UnityAdsImplementation hook - wrap UnityAds.show listener to report impressions
            try {
                installUnityAdsShowHook(sdk, logger)
            } catch (exception: Throwable) {
                logger.error("Failed to install UnityAds show hook", exception)
            }

            // report version & integration
            try {
                val unityAdsClass = Class.forName("com.unity3d.ads.UnityAds")
                val getVersionMethod = unityAdsClass.getDeclaredMethod("getVersion")
                val version = getVersionMethod.invoke(null) as String?

                logger.info(
                    "Completed full integration with Unity Ads",
                    LoggerFieldsBuilder()
                        .with("version", version ?: "null")
                        .with("bannerAds", "enabled")
                        .with("interstitialAds", "enabled")
                        .with("rewardedAds", "enabled"),
                )
            } catch (exception: ClassNotFoundException) {
                logger.error(
                    "Completed Unity integration, but failed to determine version - UnityAds class not found",
                    exception,
                )
            } catch (exception: NoSuchMethodException) {
                logger.error(
                    "Completed Unity integration, but failed to determine version - getVersion method not found",
                    exception,
                )
            } catch (exception: IllegalAccessException) {
                logger.error(
                    "Completed Unity integration, but failed to determine version - access denied",
                    exception,
                )
            } catch (exception: InvocationTargetException) {
                logger.error(
                    "Completed Unity integration, but failed to determine version - invocation error",
                    exception,
                )
            }
        } catch (exception: Throwable) {
            logger.error(
                "Failed to setup Unity integration - critical error",
                exception,
                LoggerFieldsBuilder()
                    .with("exceptionType", exception.javaClass.simpleName)
                    .with("exceptionMessage", exception.message ?: "no message"),
            )
        }
    }

    private fun installUnityAdsShowHook(
        sdk: JustTrackSdk,
        logger: Logger,
    ) {
        if (!unityAdsShowHookInstalled.compareAndSet(false, true)) {
            return
        }

        val unityAdsImplClass =
            try {
                Class.forName("com.unity3d.services.ads.UnityAdsImplementation")
            } catch (e: ClassNotFoundException) {
                logger.error("UnityAdsImplementation class not found - cannot install show hook", e)
                unityAdsShowHookInstalled.set(false)
                return
            }

        val unityAdsInterface =
            try {
                Class.forName("com.unity3d.services.ads.IUnityAds")
            } catch (e: ClassNotFoundException) {
                logger.error("IUnityAds interface not found - cannot install show hook", e)
                unityAdsShowHookInstalled.set(false)
                return
            }

        val unityAdsShowListenerClass =
            try {
                Class.forName("com.unity3d.ads.IUnityAdsShowListener")
            } catch (e: ClassNotFoundException) {
                logger.error("IUnityAdsShowListener not found - cannot install show hook", e)
                unityAdsShowHookInstalled.set(false)
                return
            }

        val unityAdsInstance =
            try {
                unityAdsImplClass.getDeclaredMethod("getInstance").invoke(null)
            } catch (e: Exception) {
                logger.error("UnityAdsImplementation.getInstance() failed - cannot install show hook", e)
                unityAdsShowHookInstalled.set(false)
                return
            }

        val showMethod =
            unityAdsInterface.methods.firstOrNull { it.name == "show" && it.parameterTypes.size == 4 }
                ?: run {
                    logger.error("UnityAds show method not found on IUnityAds")
                    unityAdsShowHookInstalled.set(false)
                    return
                }

        val proxy =
            ProxyUtils.createProxy(
                unityAdsInterface,
                unityAdsInstance,
                showMethod,
                { args ->
                    val wrappedArgs = args.clone()
                    val originalListener = args[3]
                    wrappedArgs[3] =
                        createShowListenerWrapper(
                            unityAdsShowListenerClass,
                            originalListener,
                            sdk,
                            logger,
                        )

                    try {
                        showMethod.invoke(unityAdsInstance, *wrappedArgs)
                    } catch (exception: Throwable) {
                        logger.error("Failed to delegate UnityAds show call", exception)
                    }
                    null
                },
                logger,
            )

        val instanceField =
            try {
                unityAdsImplClass.getDeclaredField("instance")
            } catch (e: NoSuchFieldException) {
                logger.error("UnityAdsImplementation.instance field not found - cannot install show hook", e)
                unityAdsShowHookInstalled.set(false)
                return
            }

        instanceField.isAccessible = true
        instanceField[null] = proxy
        instanceField.isAccessible = false
        logger.info("Successfully installed UnityAds show hook")
    }

    private fun createShowListenerWrapper(
        unityAdsShowListenerClass: Class<*>,
        originalListener: Any?,
        sdk: JustTrackSdk,
        logger: Logger,
    ): Any {
        return java.lang.reflect.Proxy.newProxyInstance(
            unityAdsShowListenerClass.classLoader,
            arrayOf(unityAdsShowListenerClass),
        ) { _, method, methodArgs ->
            if (method.name == "onUnityAdsShowComplete" && methodArgs != null) {
                val placementId = methodArgs.getOrNull(0) as? String
                val state = methodArgs.getOrNull(1)
                val isComplete = state is Enum<*> && state.name == "COMPLETED"

                try {
                    sdk.forwardAdImpression(
                        AdImpression(
                            unit = "impression",
                            sdkName = "unity",
                            network = "unity",
                            placement = placementId,
                            state =
                                if (isComplete) {
                                    AdImpressionState.COMPLETED
                                } else {
                                    AdImpressionState.SKIPPED
                                },
                        ),
                    )
                } catch (exception: Throwable) {
                    logger.error("Failed to report Unity ad impression from show listener", exception)
                }
            }

            if (originalListener != null) {
                try {
                    return@newProxyInstance if (methodArgs != null) {
                        method.invoke(originalListener, *methodArgs)
                    } else {
                        method.invoke(originalListener)
                    }
                } catch (exception: Throwable) {
                    logger.error("Failed to forward UnityAds show listener callback", exception)
                }
            }

            null
        }
    }

    private companion object {
        private val unityAdsShowHookInstalled = AtomicBoolean(false)
    }
}
