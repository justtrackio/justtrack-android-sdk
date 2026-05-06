package io.justtrack.testapp

import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds.UnityAdsInitializationError
import com.unity3d.ads.UnityAds.UnityAdsLoadError
import com.unity3d.ads.UnityAds.UnityAdsShowCompletionState
import com.unity3d.ads.UnityAds.UnityAdsShowError
import com.unity3d.ads.UnityAds.initialize
import com.unity3d.ads.UnityAds.load
import com.unity3d.ads.UnityAds.show
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import io.justtrack.integrations.unityads.UnityAdsIntegrationAdapter
import io.justtrack.testapp.databinding.ActivityUnityAdsBinding
import java.util.concurrent.atomic.AtomicBoolean

class UnityAdsActivity : BaseActivity<ActivityUnityAdsBinding>() {
    override fun getViewBinding(): ActivityUnityAdsBinding {
        return ActivityUnityAdsBinding.inflate(layoutInflater)
    }

    override fun initialize() {
        binding.enableUnityAdsSwitch.setOnClickListener {
            initUnityAdsClick()
        }
        binding.showBannerButton.setOnClickListener {
            onBannerClick()
        }
        binding.showInterstitialButton.setOnClickListener {
            onInterstitialClick()
        }
        binding.showRewardedVideoButton.setOnClickListener { onRewardedVideoClick() }
    }

    override fun onResume() {
        super.onResume()
        binding.enableUnityAdsSwitch.setEnabled(!unityAdsInitialized.get())
    }

    private fun initUnityAdsClick() {
        if (MainApplication.sdk == null || !unityAdsInitialized.compareAndSet(false, true)) {
            Toast.makeText(
                this,
                "SDK not initialized or UnityAds already initialized",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        binding.enableUnityAdsSwitch.setEnabled(false)

        initialize(
            applicationContext,
            BuildConfig.UNITY_ADS_ID,
            true,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    try {
                        MainApplication.sdk!!.integrateWith(UnityAdsIntegrationAdapter())
                    } catch (e: Throwable) {
                        Log.e(TAG, "error integrating with Unity Ads", e)
                    }
                    runOnUiThread {
                        binding.showBannerButton.setEnabled(true)
                        binding.showInterstitialButton.setEnabled(true)
                        binding.showRewardedVideoButton.setEnabled(true)
                    }
                }

                override fun onInitializationFailed(
                    error: UnityAdsInitializationError,
                    message: String,
                ) {
                    Log.e(
                        TAG,
                        "onInitializationFailed with error $error and message $message",
                    )
                }
            },
        )
    }

    private fun onBannerClick() {
        showBannerAd("Banner_Android")
    }

    private fun onInterstitialClick() {
        showAd("Interstitial_Android")
    }

    private fun onRewardedVideoClick() {
        showAd("Rewarded_Android")
    }

    private fun showBannerAd(placementId: String) {
        val banner = BannerView(this, placementId, UnityBannerSize(320, 50))
        banner.listener =
            object : BannerView.Listener() {
                override fun onBannerLoaded(bannerAdView: BannerView) {
                    super.onBannerLoaded(bannerAdView)
                    Log.i(TAG, "onBannerLoaded $bannerAdView")
                }

                override fun onBannerFailedToLoad(
                    bannerAdView: BannerView,
                    errorInfo: BannerErrorInfo,
                ) {
                    super.onBannerFailedToLoad(bannerAdView, errorInfo)
                    Log.i(
                        TAG,
                        "onBannerFailedToLoad " +
                            bannerAdView +
                            " with error " +
                            errorInfo.errorCode +
                            " and message " +
                            errorInfo.errorMessage,
                    )
                }

                override fun onBannerClick(bannerAdView: BannerView) {
                    super.onBannerClick(bannerAdView)
                    Log.i(TAG, "onBannerClick $bannerAdView")
                }

                override fun onBannerLeftApplication(bannerAdView: BannerView) {
                    super.onBannerLeftApplication(bannerAdView)
                    Log.i(
                        TAG,
                        "onBannerLeftApplication $bannerAdView",
                    )
                }
            }

        banner.load()
        val bannerContainer: FrameLayout = binding.bannerContainer
        bannerContainer.addView(banner)
        binding.showBannerButton.setEnabled(false)

        Thread {
            try {
                Thread.sleep(15000)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            runOnUiThread {
                bannerContainer.removeAllViews()
                banner.destroy()
                binding.showBannerButton.setEnabled(true)
            }
        }.start()
    }

    private fun showAd(placementId: String) {
        load(
            placementId,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    show(
                        this@UnityAdsActivity,
                        placementId,
                        object : IUnityAdsShowListener {
                            override fun onUnityAdsShowFailure(
                                placementId: String,
                                error: UnityAdsShowError,
                                message: String,
                            ) {
                                Log.e(
                                    TAG,
                                    "onUnityAdsShowFailure $placementId with error $error and message $message",
                                )
                            }

                            override fun onUnityAdsShowStart(placementId: String) {
                                Log.i(
                                    TAG,
                                    "onUnityAdsShowStart $placementId",
                                )
                            }

                            override fun onUnityAdsShowClick(placementId: String) {
                                Log.i(
                                    TAG,
                                    "onUnityAdsShowClick $placementId",
                                )
                            }

                            override fun onUnityAdsShowComplete(
                                placementId: String,
                                state: UnityAdsShowCompletionState,
                            ) {
                                Log.i(
                                    TAG,
                                    "onUnityAdsShowComplete $placementId with state $state",
                                )
                            }
                        },
                    )
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAdsLoadError,
                    message: String,
                ) {
                    Log.e(
                        TAG,
                        "onUnityAdsFailedToLoad $placementId with error $error and message $message",
                    )
                }
            },
        )
    }

    companion object {
        private const val TAG = "TestAppUnityAds"
        private val unityAdsInitialized = AtomicBoolean(false)
    }
}
