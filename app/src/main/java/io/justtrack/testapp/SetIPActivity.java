package io.justtrack.testapp;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.justtrack.ApplicationVersion;
import io.justtrack.testapp.databinding.ActivitySetIpBinding;

public class SetIPActivity extends BaseActivity<ActivitySetIpBinding> {
    private static final String TAG = "SetIPActivity";

    @Override
    protected ActivitySetIpBinding getViewBinding() {
        return ActivitySetIpBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initialize() {
        binding.startAppButton.setOnClickListener(this::onStartAppClick);
        binding.configureCustomUserIdButton.setOnClickListener(this::onConfigureCustomUserIdClick);

        String applicationPackageName = this.getPackageName();
        ApplicationVersionImpl applicationVersion = getDefaultApplicationVersion();

        binding.customPackageNameEditText.setText(applicationPackageName);
        binding.customVersionNameEditText.setText(applicationVersion != null ? applicationVersion.versionName : "");
        binding.customVersionCodeEditText.setText(applicationVersion != null ? applicationVersion.versionCode : "");

        binding.clearCustomPacakgeNameButton.setOnClickListener(view -> {
            binding.customPackageNameEditText.setText("");
        });

        binding.clearAppVersionButton.setOnClickListener(view -> {
            binding.customVersionNameEditText.setText("");
            binding.customVersionCodeEditText.setText("");
        });
    }

    @Override
    protected void onResume() {
        if (MainApplication.sdk != null) {
            MainApplication.sdk.shutdown();
            MainApplication.sdk = null;
        }

        super.onResume();
    }

    private void onStartAppClick(View view) {
        assert view != null; // use it somehow

        boolean isManualStart = binding.manualStartSwitch.isChecked();
        boolean isAutoIAPTracking = binding.autoIapSwitch.isChecked();
        boolean isEnabledLog = binding.enabledLogSwitch.isChecked();
        boolean addCustomLog = binding.addCustomLog.isChecked();

        String configurePackageName = binding.customPackageNameEditText.getText().toString();
        String configureAppVersionName = binding.customVersionNameEditText.getText().toString();
        String configureAppVersionCode = binding.customVersionCodeEditText.getText().toString();

        Intent startIntent = new Intent(new Intent(this, MainActivity.class));
        startIntent.putExtra("manual_start", isManualStart);
        startIntent.putExtra("auto_iap", isAutoIAPTracking);
        startIntent.putExtra("enabled_log", isEnabledLog);
        startIntent.putExtra("add_custom_log", addCustomLog);

        if (!configurePackageName.isEmpty()) {
            startIntent.putExtra("configure_package_name", configurePackageName);
        }

        if (!configureAppVersionName.isEmpty()) {
            startIntent.putExtra("configure_version_name", configureAppVersionName);
        }

        if (!configureAppVersionCode.isEmpty()) {
            startIntent.putExtra("configure_version_code", configureAppVersionCode);
        }

        startActivity(startIntent);
    }

    private void onConfigureCustomUserIdClick(View view) {
        assert view != null; // use it somehow

        startActivity(new Intent(this, SetCustomUserIdActivity.class));
    }

    @Nullable
    private ApplicationVersionImpl getDefaultApplicationVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return new ApplicationVersionImpl(pInfo.versionName, String.valueOf(pInfo.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private class ApplicationVersionImpl implements ApplicationVersion {
        private final String versionName;
        private final String versionCode;

        ApplicationVersionImpl(String versionName, String versionCode) {
            this.versionName = versionName;
            this.versionCode = versionCode;
        }

        @NonNull
        @Override
        public String getVersionCode() {
            return versionCode;
        }

        @NonNull
        @Override
        public String getVersionName() {
            return versionName;
        }
    }
}