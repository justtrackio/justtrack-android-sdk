package io.justtrack.testapp;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.unity3d.mediation.LevelPlay;
import com.unity3d.mediation.LevelPlayConfiguration;
import com.unity3d.mediation.LevelPlayInitError;
import com.unity3d.mediation.LevelPlayInitListener;
import com.unity3d.mediation.LevelPlayInitRequest;

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
        LevelPlay.setMetaData("is_test_suite", "enable");
        binding.ironsourceEnableSwitch.setOnClickListener(this::initIronSourceClick);
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
            LevelPlay.setConsent(true);
            // If the sdk builder apply customId already please remove this line.
            runOnUiThread(() -> {
                LevelPlayInitRequest.Builder builder = new LevelPlayInitRequest.Builder(BuildConfig.IRONSOURCE_KEY);
                if (customUserId != null) {
                    builder.withUserId(customUserId);
                }
                LevelPlayInitRequest initRequest = builder.build();
                LevelPlayInitListener initListener = new LevelPlayInitListener() {
                    @Override
                    public void onInitFailed(@NonNull LevelPlayInitError error) {
                        Log.e(TAG, "onInitFailed: " + error.getErrorMessage() );
                    }
                    @Override
                    public void onInitSuccess(LevelPlayConfiguration configuration) {
                        MainApplication.sdk.integrateWith(new IronSourceIntegrationAdapter());
                        LevelPlay.launchTestSuite(IronSourceActivity.this);
                    }
                };
                LevelPlay.init(this, initRequest, initListener);
            });
        }).start();
    }
}
