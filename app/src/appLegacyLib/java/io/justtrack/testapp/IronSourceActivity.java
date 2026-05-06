package io.justtrack.testapp;

import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.ironsource.mediationsdk.ISBannerSize;
import com.ironsource.mediationsdk.IronSource;
import com.ironsource.mediationsdk.IronSourceBannerLayout;
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo;
import com.ironsource.mediationsdk.integration.IntegrationHelper;
import com.ironsource.mediationsdk.logger.IronSourceError;
import com.ironsource.mediationsdk.model.Placement;
import com.ironsource.mediationsdk.sdk.LevelPlayBannerListener;
import com.ironsource.mediationsdk.sdk.LevelPlayInterstitialListener;
import com.ironsource.mediationsdk.sdk.LevelPlayRewardedVideoListener;

import java.util.concurrent.atomic.AtomicBoolean;

import io.justtrack.integrations.ironsource.IronSourceIntegrationAdapter;
import io.justtrack.testapp.databinding.ActivityIronSourceBinding;

public class IronSourceActivity extends BaseActivity<ActivityIronSourceBinding> {
    private static final String TAG = "TestAppIronSource";
    private final static AtomicBoolean ironsourceInitialized = new AtomicBoolean(false);

    @Override
    protected ActivityIronSourceBinding getViewBinding() {
        return ActivityIronSourceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initialize() {
        binding.ironsourceEnableSwitch.setOnClickListener(this::initIronSourceClick);
        binding.showInterstitialButton.setOnClickListener(this::onInterstitialClick);
        binding.showRewardedVideoButton.setOnClickListener(this::onRewardedVideoClick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ironsourceInitialized.get()) {
            IronSource.onPause(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.ironsourceEnableSwitch.setEnabled(!ironsourceInitialized.get());
        if (ironsourceInitialized.get()) {
            IronSource.onResume(this);
        }
    }

    private void initIronSourceClick(View view) {
        assert view != null; // use it somehow
        if (MainApplication.sdk == null || !ironsourceInitialized.compareAndSet(false, true)) {
            Toast.makeText(this, "SDK not initialized or IronSource already initialized", Toast.LENGTH_LONG).show();
            return;
        }

        binding.ironsourceEnableSwitch.setEnabled(false);

        new Thread(() -> {
            @Nullable String customUserIdInput = binding.customIdEditText.getText().toString();
            @Nullable String customUserId;
            if (customUserIdInput.isEmpty()) {
                customUserId = null;
            } else {
                customUserId = customUserIdInput;
            }
            // If the sdk builder apply customId already please remove this line.
            runOnUiThread(() -> {
                IronSource.setConsent(true);
                IronSource.init(
                        this,
                        BuildConfig.IRONSOURCE_KEY,
                        IronSource.AD_UNIT.REWARDED_VIDEO,
                        IronSource.AD_UNIT.INTERSTITIAL,
                        IronSource.AD_UNIT.BANNER
                );
                IntegrationHelper.validateIntegration(this);

                MainApplication.sdk.integrateWith(new IronSourceIntegrationAdapter(customUserId));

                IronSource.setLevelPlayInterstitialListener(new LevelPlayInterstitialListener() {

                    @Override
                    public void onAdReady(AdInfo adInfo) {
                        Log.d(TAG, "onInterstitialAdReady called");
                        runOnUiThread(() -> binding.showInterstitialButton.setEnabled(true));
                    }

                    @Override
                    public void onAdLoadFailed(IronSourceError ironSourceError) {
                        Log.d(TAG, "onInterstitialAdLoadFailed called " + ironSourceError);
                    }

                    @Override
                    public void onAdOpened(AdInfo adInfo) {
                        Log.d(TAG, "onInterstitialAdOpened called");
                    }

                    @Override
                    public void onAdShowSucceeded(AdInfo adInfo) {
                        Log.d(TAG, "onInterstitialAdShowSucceeded called");
                    }

                    @Override
                    public void onAdShowFailed(IronSourceError ironSourceError, AdInfo adInfo) {
                        Log.d(TAG, "onInterstitialAdShowFailed called " + ironSourceError);
                    }

                    @Override
                    public void onAdClicked(AdInfo adInfo) {
                        Log.d(TAG, "onInterstitialAdClicked called");
                    }

                    @Override
                    public void onAdClosed(AdInfo adInfo) {
                        Log.d(TAG, "onInterstitialAdClosed called");
                        IronSource.loadInterstitial();
                    }
                });
                IronSource.setLevelPlayRewardedVideoListener(new LevelPlayRewardedVideoListener() {
                    @Override
                    public void onAdOpened(AdInfo adInfo) {
                        Log.d(TAG, "onRewardedVideoAdOpened called");
                    }

                    @Override
                    public void onAdShowFailed(IronSourceError ironSourceError, AdInfo adInfo) {
                        Log.d(TAG, "onRewardedVideoAdShowFailed called " + ironSourceError);
                    }

                    @Override
                    public void onAdClicked(Placement placement, AdInfo adInfo) {
                        Log.d(TAG, "onRewardedVideoAdClicked called " + placement);
                    }

                    @Override
                    public void onAdRewarded(Placement placement, AdInfo adInfo) {
                        Log.d(TAG, "onRewardedVideoAdRewarded called " + placement);
                        IronSource.loadRewardedVideo();
                    }

                    @Override
                    public void onAdClosed(AdInfo adInfo) {
                        Log.d(TAG, "onRewardedVideoAdClosed called");
                    }

                    @Override
                    public void onAdAvailable(AdInfo adInfo) {
                        Log.d(TAG, "onRewardedVideoAvailable called ");
                        runOnUiThread(() -> binding.showRewardedVideoButton.setEnabled(true));
                    }

                    @Override
                    public void onAdUnavailable() {
                        Log.d(TAG, "onRewardedVideoUnAvailable called ");
                        runOnUiThread(() -> binding.showRewardedVideoButton.setEnabled(false));
                    }
                });

                IronSource.loadInterstitial();
                IronSource.loadRewardedVideo();

                IronSourceBannerLayout banner = IronSource.createBanner(this, ISBannerSize.SMART);
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                binding.bannerContainer.addView(banner, 0, layoutParams);

                banner.setLevelPlayBannerListener(new LevelPlayBannerListener() {
                    @Override
                    public void onAdLoaded(AdInfo adInfo) {
                        Log.d(TAG, "onBannerAdLoaded called");
                    }

                    @Override
                    public void onAdLoadFailed(IronSourceError ironSourceError) {
                        Log.d(TAG, "onBannerAdLoadFailed called " + ironSourceError);
                        runOnUiThread(() -> {
                            Log.d(TAG, "run called");
                            binding.bannerContainer.removeAllViews();
                        });
                    }

                    @Override
                    public void onAdClicked(AdInfo adInfo) {
                        Log.d(TAG, "onBannerAdClicked called");
                    }

                    @Override
                    public void onAdLeftApplication(AdInfo adInfo) {
                        Log.d(TAG, "onBannerAdLeftApplication called");
                    }

                    @Override
                    public void onAdScreenPresented(AdInfo adInfo) {
                        Log.d(TAG, "onBannerAdScreenPresented called");
                    }

                    @Override
                    public void onAdScreenDismissed(AdInfo adInfo) {
                        Log.d(TAG, "onBannerAdScreenDismissed called");
                    }
                });

                IronSource.loadBanner(banner);
            });
        }).start();
    }

    private void onInterstitialClick(View view) {
        assert view != null; // use it somehow
        Log.d(TAG, "Interstitial ready " + IronSource.isInterstitialReady());
        IronSource.showInterstitial();
        binding.showInterstitialButton.setEnabled(false);
    }

    private void onRewardedVideoClick(View view) {
        assert view != null; // use it somehow
        Log.d(TAG, "Rewarded video ready " + IronSource.isRewardedVideoAvailable());
        IronSource.showRewardedVideo();
        binding.showRewardedVideoButton.setEnabled(false);
    }
}
