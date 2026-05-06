package io.justtrack.testapp;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;

import java.util.concurrent.atomic.AtomicBoolean;

import io.justtrack.integrations.applovin.AppLovinMaxIntegrationAdapter;
import io.justtrack.integrations.applovin.AppLovinSimpleIntegrationAdapter;
import io.justtrack.testapp.databinding.ActivityAppLovinBinding;

public class AppLovinActivity extends BaseActivity<ActivityAppLovinBinding> {
    private static final String TAG = "TestAppAppLovin";
    private final static AtomicBoolean appLovinInitialized = new AtomicBoolean(false);

    @Override
    protected ActivityAppLovinBinding getViewBinding() {
        return ActivityAppLovinBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initialize() {
        binding.appLovinEnableSwitch.setOnClickListener(this::initAppLovinClick);
        binding.showMediationDebuggerButton.setOnClickListener(this::onShowMediationDebuggerClick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.appLovinEnableSwitch.setEnabled(!appLovinInitialized.get());
    }

    private void initAppLovinClick(View view) {
        assert view != null; // use it somehow
        if (MainApplication.sdk == null || !appLovinInitialized.compareAndSet(false, true)) {
            Toast.makeText(this, "SDK not initialized or AppLovin already initialized", Toast.LENGTH_LONG).show();
            return;
        }

        @Nullable String customUserIdInput = binding.customIdEditText.getText().toString();
        @Nullable String customUserId;
        if (customUserIdInput.isEmpty()) {
            customUserId = null;
        } else {
            customUserId = customUserIdInput;
        }

        final boolean useMaxIntegration = binding.integrateWithAppLovinMax.isChecked();
        final boolean useSimpleIntegration = binding.integrateWithAppLovin.isChecked();

        if (useMaxIntegration && useSimpleIntegration) {
            Toast.makeText(this, "Can not integrate with both methods at the same time", Toast.LENGTH_LONG).show();
            return;
        }

        if (!(useSimpleIntegration || useMaxIntegration)) {
            Toast.makeText(this, "Need to select at least one method", Toast.LENGTH_LONG).show();
            return;
        }

        binding.appLovinEnableSwitch.setEnabled(false);
        AppLovinSdk appLovin = AppLovinSdk.getInstance(this);
        appLovin.initialize(
                AppLovinSdkInitializationConfiguration
                        .builder(BuildConfig.APPLOVIN_KEY)
                        .setMediationProvider(AppLovinMediationProvider.MAX)
                        .build(),
                appLovinSdkConfiguration -> {
                    try {
                        if (useSimpleIntegration) {
                            MainApplication.sdk.integrateWith(new AppLovinSimpleIntegrationAdapter());
                        } else {
                            MainApplication.sdk.integrateWith(new AppLovinMaxIntegrationAdapter(customUserId));
                        }

                        runOnUiThread(() -> binding.showMediationDebuggerButton.setEnabled(true));
                        Log.i(TAG, "Integrated with AppLovin");
                    } catch (Throwable e) {
                        Log.e(TAG, "error integrating with AppLovin", e);
                    }
                });
    }

    private void onShowMediationDebuggerClick(View view) {
        assert view != null; // use it somehow
        AppLovinSdk.getInstance(this).showMediationDebugger();
    }
}
