package io.justtrack.testapp;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chartboost.sdk.Chartboost;
import com.chartboost.sdk.ads.Ad;
import com.chartboost.sdk.ads.Banner;
import com.chartboost.sdk.ads.Interstitial;
import com.chartboost.sdk.ads.Rewarded;
import com.chartboost.sdk.callbacks.BannerCallback;
import com.chartboost.sdk.callbacks.InterstitialCallback;
import com.chartboost.sdk.callbacks.RewardedCallback;
import com.chartboost.sdk.events.CacheError;
import com.chartboost.sdk.events.CacheEvent;
import com.chartboost.sdk.events.ClickError;
import com.chartboost.sdk.events.ClickEvent;
import com.chartboost.sdk.events.DismissEvent;
import com.chartboost.sdk.events.ImpressionEvent;
import com.chartboost.sdk.events.RewardEvent;
import com.chartboost.sdk.events.ShowError;
import com.chartboost.sdk.events.ShowEvent;

import java.util.concurrent.atomic.AtomicBoolean;

import io.justtrack.integrations.charboost.ChartboostIntegrationAdapter;
import io.justtrack.testapp.databinding.ActivityChartboostBinding;

public class ChartboostActivity extends BaseActivity<ActivityChartboostBinding> {
    private static final String TAG = "TestAppChartboost";
    private final static AtomicBoolean chartboostInitialized = new AtomicBoolean(false);

    @Override
    protected ActivityChartboostBinding getViewBinding() {
        return ActivityChartboostBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initialize() {
        binding.ChartboostEnableSwitch.setOnClickListener(this::initChartboostClick);
        binding.showBannerButton.setOnClickListener(this::onBannerClick);
        binding.showInterstitialButton.setOnClickListener(this::onInterstitialClick);
        binding.showRewardedVideoButton.setOnClickListener(this::onRewardedVideoClick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.ChartboostEnableSwitch.setEnabled(!chartboostInitialized.get());
    }

    private void initChartboostClick(View view) {
        assert view != null; // use it somehow
        if (MainApplication.sdk == null || !chartboostInitialized.compareAndSet(false, true)) {
            Toast.makeText(this, "SDK not initialized or Chartboost already initialized", Toast.LENGTH_LONG).show();
            return;
        }

        binding.ChartboostEnableSwitch.setEnabled(false);

        Chartboost.startWithAppId(getApplicationContext(), BuildConfig.CHARTBOOST_APP_ID, BuildConfig.CHARTBOOST_KEY, startError -> {
            if (startError == null) {
                try {
                    MainApplication.sdk.integrateWith(new ChartboostIntegrationAdapter());
                } catch (Throwable e) {
                    Log.e(TAG, "error integrating with Chartboost", e);
                }
                runOnUiThread(() -> {
                    binding.showBannerButton.setEnabled(true);
                    binding.showInterstitialButton.setEnabled(true);
                    binding.showRewardedVideoButton.setEnabled(true);
                });
            } else {
                Log.e(TAG, "onStartCompleted error " + startError.getCode());
            }
        });
    }

    private void onBannerClick(View view) {
        assert view != null; // use it somehow
        Banner banner = new Banner(this, "Test Banner", Banner.BannerSize.STANDARD, new BannerCallback() {
            @Override
            public void onAdLoaded(@NonNull CacheEvent cacheEvent, @Nullable CacheError cacheError) {
                if (cacheError == null) {
                    Log.i(TAG, "onAdLoaded " + cacheEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdLoaded " + cacheEvent.getAdID() + " with error " + cacheError.getCode());
                }
            }

            @Override
            public void onAdRequestedToShow(@NonNull ShowEvent showEvent) {
                Log.i(TAG, "onAdRequestedToShow " + showEvent.getAdID());
            }

            @Override
            public void onAdShown(@NonNull ShowEvent showEvent, @Nullable ShowError showError) {
                if (showError == null) {
                    Log.i(TAG, "onAdShown " + showEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdShown " + showEvent.getAdID() + " with error " + showError.getCode());
                }
            }

            @Override
            public void onAdClicked(@NonNull ClickEvent clickEvent, @Nullable ClickError clickError) {
                if (clickError == null) {
                    Log.i(TAG, "onAdClicked " + clickEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdLoaded " + clickEvent.getAdID() + " with error " + clickError.getCode());
                }
            }

            @Override
            public void onImpressionRecorded(@NonNull ImpressionEvent impressionEvent) {
                Log.i(TAG, "onImpressionRecorded " + impressionEvent.getAdID());
            }
        }, null);

        FrameLayout bannerContainer = binding.bannerContainer;
        bannerContainer.addView(banner);
        cacheAndShow(banner);
        binding.showBannerButton.setEnabled(false);

        new Thread(() -> {
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            runOnUiThread(() -> {
                bannerContainer.removeAllViews();
                banner.detach();
                binding.showBannerButton.setEnabled(true);
            });
        }).start();
    }

    private void onInterstitialClick(View view) {
        assert view != null; // use it somehow
        Interstitial interstitial = new Interstitial("Interstitial Test", new InterstitialCallback() {
            @Override
            public void onAdDismiss(@NonNull DismissEvent dismissEvent) {
                Log.i(TAG, "onAdDismiss " + dismissEvent.getAdID());
            }

            @Override
            public void onImpressionRecorded(@NonNull ImpressionEvent impressionEvent) {
                Log.i(TAG, "onImpressionRecorded " + impressionEvent.getAdID());
            }

            @Override
            public void onAdShown(@NonNull ShowEvent showEvent, @Nullable ShowError showError) {
                if (showError == null) {
                    Log.i(TAG, "onAdShown " + showEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdShown " + showEvent.getAdID() + " with error " + showError.getCode());
                }
            }

            @Override
            public void onAdRequestedToShow(@NonNull ShowEvent showEvent) {
                Log.i(TAG, "onAdRequestedToShow " + showEvent.getAdID());
            }

            @Override
            public void onAdLoaded(@NonNull CacheEvent cacheEvent, @Nullable CacheError cacheError) {
                if (cacheError == null) {
                    Log.i(TAG, "onAdLoaded " + cacheEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdLoaded " + cacheEvent.getAdID() + " with error " + cacheError.getCode());
                }
            }

            @Override
            public void onAdClicked(@NonNull ClickEvent clickEvent, @Nullable ClickError clickError) {
                if (clickError == null) {
                    Log.i(TAG, "onAdClicked " + clickEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdLoaded " + clickEvent.getAdID() + " with error " + clickError.getCode());
                }
            }
        }, null);
        cacheAndShow(interstitial);
    }

    private void onRewardedVideoClick(View view) {
        assert view != null; // use it somehow

        Rewarded rewarded = new Rewarded("Rewarded Video", new RewardedCallback() {
            @Override
            public void onRewardEarned(@NonNull RewardEvent rewardEvent) {
                Log.i(TAG, "onRewardEarned " + rewardEvent.getAdID());
            }

            @Override
            public void onAdDismiss(@NonNull DismissEvent dismissEvent) {
                Log.i(TAG, "onAdDismiss " + dismissEvent.getAdID());
            }

            @Override
            public void onImpressionRecorded(@NonNull ImpressionEvent impressionEvent) {
                Log.i(TAG, "onImpressionRecorded " + impressionEvent.getAdID());
            }

            @Override
            public void onAdShown(@NonNull ShowEvent showEvent, @Nullable ShowError showError) {
                if (showError == null) {
                    Log.i(TAG, "onAdShown " + showEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdShown " + showEvent.getAdID() + " with error " + showError.getCode());
                }
            }

            @Override
            public void onAdRequestedToShow(@NonNull ShowEvent showEvent) {
                Log.i(TAG, "onAdRequestedToShow " + showEvent.getAdID());
            }

            @Override
            public void onAdLoaded(@NonNull CacheEvent cacheEvent, @Nullable CacheError cacheError) {
                if (cacheError == null) {
                    Log.i(TAG, "onAdLoaded " + cacheEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdLoaded " + cacheEvent.getAdID() + " with error " + cacheError.getCode());
                }
            }

            @Override
            public void onAdClicked(@NonNull ClickEvent clickEvent, @Nullable ClickError clickError) {
                if (clickError == null) {
                    Log.i(TAG, "onAdClicked " + clickEvent.getAdID());
                } else {
                    Log.e(TAG, "onAdLoaded " + clickEvent.getAdID() + " with error " + clickError.getCode());
                }
            }
        }, null);
        cacheAndShow(rewarded);
    }

    private void cacheAndShow(Ad ad) {
        ad.cache();
        showAdWhenCached(ad);
    }

    private void showAdWhenCached(Ad ad) {
        if (ad.isCached()) {
            ad.show();
            return;
        }

        new Handler(getMainLooper()).postDelayed(() -> showAdWhenCached(ad), 100);
    }
}