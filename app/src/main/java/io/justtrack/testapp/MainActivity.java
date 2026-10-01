package io.justtrack.testapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.tabs.TabLayout;
import com.ironsource.mediationsdk.IronSource;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.justtrack.AppEvent;
import io.justtrack.Callback;
import io.justtrack.JustTrackSdk;
import io.justtrack.JustTrackSdkBuilder;
import io.justtrack.Subscription;
import io.justtrack.ads.AdImpression;
import io.justtrack.ads.AdUnit;
import io.justtrack.attribution.AdvertiserIdInfo;
import io.justtrack.attribution.Attribution;
import io.justtrack.deeplinks.DeepLinkHandled;
import io.justtrack.events.Dimension;
import io.justtrack.events.JtAdEvent;
import io.justtrack.events.JtLoginEvent;
import io.justtrack.events.JtProgressionEvent;
import io.justtrack.events.JtPurchaseEvent;
import io.justtrack.events.JtResourceEvent;
import io.justtrack.events.Money;
import io.justtrack.events.TimeUnitGroup;
import io.justtrack.exceptions.InvalidFieldException;
import io.justtrack.exceptions.SdkNotTrackingException;
import io.justtrack.integrations.firebase.FirebaseIntegrationAdapter;
import io.justtrack.retargeting.PreliminaryRetargetingParameters;
import io.justtrack.retargeting.RetargetingParameters;
import io.justtrack.testapp.databinding.ActivityMainBinding;
import io.justtrack.testapp.inapp.IAPActivity;

public class MainActivity extends BaseActivity<ActivityMainBinding> {
    private static final String TAG = "TestApp";
    private final List<Subscription> subscriptions = new ArrayList<>();
    private @Nullable Subscription intentSubscription = null;
    private boolean isAutoIap = false;

    @Override
    protected ActivityMainBinding getViewBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initialize() {
        Intent intent = getIntent();
        boolean isManualStart = intent.getBooleanExtra("manual_start", false);
        isAutoIap = intent.getBooleanExtra("auto_iap", false);
        boolean isEnabledLog = intent.getBooleanExtra("enabled_log", false);
        boolean addCustomLog = intent.getBooleanExtra("add_custom_log", false);
        @Nullable String configureAppPackageName = intent.getStringExtra("configure_package_name");
        @Nullable String configureAppVersionName = intent.getStringExtra("configure_version_name");
        @Nullable String configureAppVersionCode = intent.getStringExtra("configure_version_code");

        JustTrackSdkBuilder builder = new JustTrackSdkBuilder(this, BuildConfig.APP_KEY)
                .setInstallUncaughtExceptionHandler(true)
                .setManualStart(isManualStart)
                .setAutomaticInAppPurchaseTracking(isAutoIap)
                .setLoggingEnabled(isEnabledLog)
                .setServerUrl(BuildConfig.SERVER_URL);

        if (MainApplication.customUserId != null) {
            try {
                builder.setUserId(MainApplication.customUserId);
            } catch (InvalidFieldException e) {
                Log.e(TAG, "Failed to set custom user id", e);
            }
        }

        if (configureAppPackageName != null) {
            builder.setPackageName(configureAppPackageName);
        }

        if (configureAppVersionName != null && configureAppVersionCode != null) {
            builder.setApplicationVersion(configureAppVersionName, configureAppVersionCode);
        }

        if (addCustomLog) {
            builder.setLogger(new CustomLogger());
        }

        builder.setEnableConnectionTracking(true);

        MainApplication.initSdk(this, builder.build());

        refreshIsTracking();

        try {
            MainApplication.sdk.integrateWith(new FirebaseIntegrationAdapter());
        } catch (Exception exception) {
            Log.e(TAG, "initialize: integration with firebase failed", exception);
        }

        Spinner adSdkSpinner = binding.adSdkSpinner;
        String[] adSdks = new String[]{"ironsource", "admob", "Admob", "custom", "iceCreamFlavor"};
        ArrayAdapter<String> adSdksAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, adSdks);
        adSdkSpinner.setAdapter(adSdksAdapter);

        Spinner revenueEventSpinner = binding.revenueEventSpinner;
        String[] revenueEvents = new String[]{"mixed events", "banner events", "interstitial events", "rewarded events", "rewarded interstitial events", "app open events", "native events", "custom events"};
        ArrayAdapter<String> revenueEventsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, revenueEvents);
        revenueEventSpinner.setAdapter(revenueEventsAdapter);

        Spinner currencySpinner = binding.currencySpinner;
        String[] currencies = new String[]{"EUR", "USD", "GBP", "AUD", "JPY"};
        ArrayAdapter<String> currenciesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, currencies);
        currencySpinner.setAdapter(currenciesAdapter);

        Spinner moneyEventSpinner = binding.moneyEventSpinner;
        String[] moneyEvents = new String[]{"custom event", "product purchase", "subscription purchase"};
        ArrayAdapter<String> moneyEventsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, moneyEvents);
        moneyEventSpinner.setAdapter(moneyEventsAdapter);

        TabLayout tabLayout = binding.tabLayout;
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                hideTab(tab.getPosition());
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }
        });
        showTab(0);
        hideTab(1);
        hideTab(2);

        binding.randomIntentButton.setOnClickListener(this::onRandomIntentClick);
        binding.gameButton.setOnClickListener(this::onGameClick);
        binding.retargetingClickButton.setOnClickListener(this::onRetargetingClickClick);
        binding.retargetingReactNativeClickButton.setOnClickListener(this::onRetargetingReactNativeClickClick);
        binding.retargetingUnityClickButton.setOnClickListener(this::onRetargetingUnityClickClick);
        binding.retargetingUnityOtherClickButton.setOnClickListener(this::onRetargetingUnityOtherClickClick);
        binding.showAppLovinButton.setOnClickListener(this::onAppLovinClick);
        binding.showChartboostButton.setOnClickListener(this::onChartboostClick);
        binding.showIronsourceButton.setOnClickListener(this::onIronSourceClick);
        binding.showUnityAdsButton.setOnClickListener(this::onUnityAdsClick);
        binding.showIAPButton.setOnClickListener(this::onIAPClick);
        binding.generateRevenueButton.setOnClickListener(this::onSubmitRevenueClick);
        binding.generateMoneyButton.setOnClickListener(this::onSubmitMoneyClick);
        binding.spamEventButton.setOnClickListener(this::onSubmitSpamEventClick);
        binding.sendAllEventButton.setOnClickListener(this::onSendAllEvent);
        binding.inspectLogsButton.setOnClickListener(this::onInspectLogsClick);
        binding.configureCustomUserIdButton.setOnClickListener(this::onConfigureCustomUserIdClick);
        binding.crashButton.setOnClickListener(this::onCrashClick);
        binding.firebaseButton.setOnClickListener(this::onFirebaseClick);

        binding.startTrackingButton.setOnClickListener(this::onStartTrackingClick);
        binding.stopTrackingButton.setOnClickListener(this::onStopTrackingClick);
        binding.reFetchButton.setOnClickListener(this::reFetchButtonClick);
        binding.isTrackingRefreshButton.setOnClickListener(this::refreshIsTrackingClick);
        binding.anonymousButton.setOnClickListener(this::anonymousClick);
        binding.experimentButton.setOnClickListener(this::experimentClick);

    }

    private void refreshIsTrackingClick(View view) {
        refreshIsTracking();
    }

    private void refreshIsTracking() {
        boolean isTracking = MainApplication.sdk.isRunning();
        binding.isTrackingTextView.setText("isTracking: " + isTracking);
    }

    private void showTab(int index) {
        LinearLayout layout = index == 0 ? binding.attributionLinearLayout : index == 1 ? binding.revenueLinearLayout : binding.logsLinearLayout;
        layout.setVisibility(View.VISIBLE);
    }

    private void hideTab(int index) {
        LinearLayout layout = index == 0 ? binding.attributionLinearLayout : index == 1 ? binding.revenueLinearLayout : binding.logsLinearLayout;
        layout.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
        super.onDestroy();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            Uri data = intent.getData();
            StringBuilder text = new StringBuilder();
            text.append(MessageFormat.format("action: {0}\nurl: {1}", action, data));
            Bundle extras = intent.getExtras();
            if (extras != null) {
                for (String k : extras.keySet()) {
                    if (extras.get(k) instanceof String) {
                        text.append(MessageFormat.format("\n{0}: {1}", k, extras.getString(k)));
                    }
                }
            }
            binding.intentTextView.setText(text.toString());
        }

        if (MainApplication.sdk == null) {
            return;
        }
        JustTrackSdk sdk = MainApplication.sdk;

        subscriptions.add(sdk.registerAttributionListener(attribution -> showUserData(attribution, "Listener")));
        subscriptions.add(sdk.registerRetargetingParametersListener(parameters -> {
            Log.e(TAG, "Got retargeting parameters");
            Log.e(TAG, "Was already installed: " + parameters.wasAlreadyInstalled());
        }));
        subscriptions.add(sdk.registerPreliminaryRetargetingParametersListener(parameters -> {
            Log.e(TAG, "Got preliminary parameters");
            parameters.validate().registerCallback(new Callback<PreliminaryRetargetingParameters.ValidateResult>() {
                @Override
                public void resolve(PreliminaryRetargetingParameters.ValidateResult response) {
                    Log.e(TAG, "Valid Parameters are " + response.validParameters());
                }

                @Override
                public void reject(@NonNull Throwable exception) {
                    Log.e(TAG, "Parameters failed to validate", exception);
                }
            });
        }));

        if (intentSubscription != null) {
            intentSubscription.unsubscribe();
        }
        intentSubscription = sdk.registerDeepLinkListener(deepLink -> {
            this.runOnUiThread(() -> binding.deeplinkTextView.setText(deepLink.getUri().toString()));

            return DeepLinkHandled.DEEP_LINK_HANDLED;
        });
    }

    private void displayAttribution(JustTrackSdk sdk) {
        sdk.getInstallInstanceId().registerCallback(new Callback<String>() {
            @Override
            public void resolve(String response) {
                runOnUiThread(() -> binding.installInstanceIdTextView.setText("Install Instance Id: " + response));
            }

            @Override
            public void reject(@NonNull Throwable exception) {

            }
        });
        sdk.getAttribution().registerCallback(new Callback<Attribution>() {
            @Override
            public void resolve(Attribution response) {
                showUserData(response, "API Request");
            }

            @Override
            public void reject(@NonNull Throwable exception) {
                Log.e(TAG, "error during attribution", exception);
            }
        });
        sdk.getAdvertiserIdInfo().registerCallback(new Callback<AdvertiserIdInfo>() {
            @Override
            public void resolve(AdvertiserIdInfo info) {
                showUserData(info);
            }

            @Override
            public void reject(@NonNull Throwable exception) {
                Log.e(TAG, "error during getAdvertiserIdInfo", exception);
                runOnUiThread(() -> binding.advertiserIdTextView.setText(exception.getMessage()));
            }
        });
    }

    private void showUserData(@NonNull Attribution response, @NonNull String source) {
        Log.i(TAG, "From " + source + ": User Type: " + response.getUserType());
        Log.i(TAG, "From " + source + ": Campaign Type: " + response.getCampaign().getType());
        Log.i(TAG, "From " + source + ": Partner: " + response.getPartner().getName() + ", " + response.getPartner().getId());
    }

    private void showUserData(@NonNull AdvertiserIdInfo info) {
        this.runOnUiThread(() -> {
            String userText = "Advertiser Id: " + info.getAdvertiserId() +
                    "\nLimited: " + info.isLimitedAdTracking();
            binding.advertiserIdTextView.setText(userText);
        });
        Log.i(TAG, "Advertiser Id: " + info.getAdvertiserId());
        Log.i(TAG, "Limited Ad Tracking: " + info.isLimitedAdTracking());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (intentSubscription != null) {
            intentSubscription.unsubscribe();
            intentSubscription = null;
        }
    }

    private void onSubmitRevenueClick(View view) {
        if (MainApplication.sdk == null) {
            return;
        }

        assert view != null; // use it somehow
        Random r = new Random();
        AdUnit[] adUnits = AdUnit.values();
        String[] placements = new String[]{"top left", "bottom", "default"};
        String[] segments = new String[]{"mysegmentname", "otherSegmentName"};
        String[] instances = new String[]{"my_instance_name", "not my instance name"};

        String placement = placements[r.nextInt(placements.length)];
        String testGroup = r.nextDouble() > 0.66 ? "B" : "A";
        String segment = segments[r.nextInt(segments.length)];
        String instance = instances[r.nextInt(instances.length)];
        double revenue = r.nextDouble();

        EditText countField = binding.revenueCountEditText;
        int count;
        try {
            count = Integer.parseInt(countField.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Failed to parse count", Toast.LENGTH_LONG).show();
            return;
        }

        Spinner adSdkSpinner = binding.adSdkSpinner;
        String sdkName = (String) adSdkSpinner.getSelectedItem();

        Spinner revenueEventSpinner = binding.revenueEventSpinner;
        int revenueEvent = revenueEventSpinner.getSelectedItemPosition();

        for (int i = 0; i < count; i++) {
            AdUnit adUnit;
            switch (revenueEvent) {
                case 0:
                    adUnit = adUnits[r.nextInt(adUnits.length)];
                    break;
                case 1:
                    adUnit = AdUnit.Banner;
                    break;
                case 2:
                    adUnit = AdUnit.Interstitial;
                    break;
                case 3:
                    adUnit = AdUnit.Rewarded;
                    break;
                case 4:
                    adUnit = AdUnit.RewardedInterstitial;
                    break;
                case 5:
                    adUnit = AdUnit.AppOpen;
                    break;
                case 6:
                    adUnit = AdUnit.Native;
                    break;
                case 7:
                default:
                    adUnit = null;
                    break;
            }
            try {
                if (adUnit != null) {
                    MainApplication.sdk.forwardAdImpression(
                            new AdImpression(
                                    adUnit,
                                    sdkName,
                                    "ajAds",
                                    placement,
                                    testGroup,
                                    segment,
                                    instance,
                                    null,
                                    new Money(revenue, "USD"),
                                    null
                            )
                    );
                } else {
                    MainApplication.sdk.forwardAdImpression(
                            new AdImpression(
                                    "customAdFormat",
                                    sdkName,
                                    "ajAds",
                                    placement,
                                    testGroup,
                                    segment,
                                    instance,
                                    null,
                                    new Money(revenue, "USD"),
                                    null
                            )
                    );
                }
            } catch (SdkNotTrackingException exception) {
                Log.e(TAG, "onSubmitRevenueClick: ", exception);
            }
        }
        Toast.makeText(this, "Submitted " + count + " event(s)", Toast.LENGTH_LONG).show();
    }

    private void onSubmitMoneyClick(View view) {
        if (MainApplication.sdk == null) {
            return;
        }

        assert view != null; // use it somehow
        Random r = new Random();

        EditText countField = binding.moneyCountEditText;
        int count;
        try {
            count = Integer.parseInt(countField.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Failed to parse count", Toast.LENGTH_LONG).show();
            return;
        }

        Spinner currencySpinner = binding.currencySpinner;
        String currency = (String) currencySpinner.getSelectedItem();

        Spinner moneyEventSpinner = binding.moneyEventSpinner;
        int moneyEvent = moneyEventSpinner.getSelectedItemPosition();

        StringBuilder token = new StringBuilder();
        while (token.length() < 10000) {
            token.append("not a token");
        }

        for (int i = 0; i < count; i++) {
            switch (moneyEvent) {
                case 0:
                    MainApplication.sdk.publishEvent(new AppEvent("custom_money_event", new Money(r.nextDouble(), currency)));
                    break;
                case 1:
                    MainApplication.sdk.forwardInApp("test product id", token.toString(), new Money(r.nextDouble(), currency));
                    break;
                case 2:
                    MainApplication.sdk.forwardSubscription("test product id", token.toString(), new Money(r.nextDouble(), currency));
                    break;
            }
        }
        Toast.makeText(this, "Submitted " + count + " event(s)", Toast.LENGTH_LONG).show();
    }

    public void onSubmitSpamEventClick(View view) {
        if (MainApplication.sdk == null) {
            return;
        }

        assert view != null; // use it somehow

        int count;
        try {
            count = Integer.parseInt(binding.spamEventCountField.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Failed to parse count", Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            for (int i = 0; i < count; i++) {
                AppEvent event = new JtProgressionEvent("start " + i, null, null, null);
                MainApplication.sdk.publishEvent(event);
            }
            this.runOnUiThread(() -> Toast.makeText(this, "Submitted " + count + " log event(s)", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void onSendAllEvent(View view) {
        assert view != null;

        JustTrackSdk sdk = MainApplication.sdk;

        sdk.track(new JtProgressionEvent("start", "world", "path", "boss"));
        sdk.track(new JtProgressionEvent("start", "world2", "path2", "boss2", 5.0, TimeUnitGroup.MILLISECONDS));

        sdk.track(new JtResourceEvent("source", "weapon", "Black Sword", "45"));
        sdk.track(new JtResourceEvent("source", "weapon", "White Sword", "42", 1));

        sdk.track(new JtPurchaseEvent(JtPurchaseEvent.Action.VIEW, "1", null, "purchase", 1));
        sdk.track(new JtPurchaseEvent(JtPurchaseEvent.Action.CLICK, "1", null, "purchase", 1));
        sdk.track(new JtPurchaseEvent("click", "2", null, "subscription", 2));

        sdk.track(new JtAdEvent(
                "clicked",
                "io.test",
                null,
                "justtrack",
                "bottom",
                null,
                null,
                "banner",
                "1"
        ));

        sdk.track(new JtAdEvent(
                "display",
                "io.test",
                null,
                "justtrack",
                null,
                null,
                null,
                "reward",
                "1",
                3.0,
                TimeUnitGroup.SECONDS
        ));

        sdk.track(new JtLoginEvent("success", "fb"));
        sdk.track(new AppEvent("attack"));
        sdk.track(
                new AppEvent("new_dimensions")
                        .addDimension(Dimension.JT_CATEGORY, "new_category")
                        .addDimension(Dimension.JT_CONTEXT, "new_context")
                        .addDimension(Dimension.JT_DETAIL, "new_detail")
                        .addDimension(Dimension.JT_LOCATION, "new_location")
                        .addDimension(Dimension.JT_STATE, "new_state")
                        .addDimension(Dimension.JT_TRIGGER, "new_trigger")
        );
    }

    private void onInspectLogsClick(View view) {
        assert view != null; // use it somehow

        new Thread(() -> {
            long count = MainApplication.getStoredLogCount();
            runOnUiThread(() ->
                    Toast.makeText(
                            this,
                            "Currently " + count + " log message(s) and metric(s) are stored on this device",
                            Toast.LENGTH_SHORT
                    ).show());
        }).start();
    }

    private void onConfigureCustomUserIdClick(View view) {
        assert view != null; // use it somehow

        startActivity(new Intent(this, SetCustomUserIdActivity.class));
    }

    private void onRandomIntentClick(View view) {
        assert view != null; // use it somehow
        String host = System.currentTimeMillis() % 10 < 5 ? "game" : "main";
        String path = "?data=" + System.currentTimeMillis();
        performIntentClick(getString(R.string.scheme), host, path);
    }

    private void onGameClick(View view) {
        if (MainApplication.sdk == null) {
            return;
        }

        assert view != null; // use it somehow
        MainApplication.sdk.publishEvent(new JtProgressionEvent("level_1", null, null, null));
        startActivity(new Intent(this, GameActivity.class));
    }

    private void onAppLovinClick(View view) {
        assert view != null; // use it somehow
        startActivity(new Intent(this, AppLovinActivity.class));
    }

    private void onChartboostClick(View view) {
        assert view != null; // use it somehow
        startActivity(new Intent(this, ChartboostActivity.class));
    }

    private void onIronSourceClick(View view) {
        assert view != null; // use it somehow
        startActivity(new Intent(this, IronSourceActivity.class));
    }

    private void onUnityAdsClick(View view) {
        assert view != null; // use it somehow
        startActivity(new Intent(this, UnityAdsActivity.class));
    }

    private void onIAPClick(View view) {
        assert view != null; // use it somehow
        Intent intent = new Intent(this, IAPActivity.class);
        intent.putExtra("auto_iap", isAutoIap);
        startActivity(intent);
    }

    private void onRetargetingClickClick(View view) {
        assert view != null; // use it somehow
        performRetargetingClick("io.justtrack.testapp", "a28923ca-c7b7-45c1-8188-5dc4b790611d", "buildWithMarketingSdk");
    }

    private void onRetargetingReactNativeClickClick(View view) {
        assert view != null; // use it somehow
        performRetargetingClick("info.applike.applikeattribution.test", "272872ed-49c2-4827-bbbb-8a992eadf0ba", "buildWithReactNativeMarketingSdk");
    }

    private void onRetargetingUnityClickClick(View view) {
        assert view != null; // use it somehow
        performRetargetingClick("io.justtrack.unity.testapp", "8f3b58d1-2a34-46d2-9e39-bd4eba50baf0", "mainisusuallyafunction");
    }

    private void onRetargetingUnityOtherClickClick(View view) {
        assert view != null; // use it somehow
        performRetargetingClick("io.justtrack.unity.testapp", "51ab3819-8d70-475a-b6a4-0487f9dc71f5", "valuecannotbeempty");
    }

    private void onCrashClick(View view) {
        assert view != null; // use it somehow
        startActivity(new Intent(this, CrashActivity.class));
    }

    private void onFirebaseClick(View view) {
        assert view != null; // use it somehow
        startActivity(new Intent(this, FirebaseActivity.class));
    }

    private void onStartTrackingClick(View view) {
        assert view != null;
        MainApplication.sdk.start();
        refreshIsTracking();
        displayAttribution(MainApplication.sdk);

        new Thread(() -> {
            try {
                @Nullable PreliminaryRetargetingParameters preliminaryRetargetingParameters = MainApplication.sdk.getPreliminaryRetargetingParameters();
                if (preliminaryRetargetingParameters != null) {
                    Log.e(TAG, "Preliminary retargeting uri: " + preliminaryRetargetingParameters.getUri());
                    Log.e(TAG, "Preliminary promotion code: " + preliminaryRetargetingParameters.getPromotionParameter());
                    for (Map.Entry<String, String> entry : preliminaryRetargetingParameters.getParameters().entrySet()) {
                        Log.e(TAG, "Preliminary retargeting parameter " + entry.getKey() + ": " + entry.getValue());
                    }
                    MainActivity.this.runOnUiThread(() -> binding.retargetingTextView.setText(MessageFormat.format("preliminary:\nuri: {0}\npromotion code: {1}", preliminaryRetargetingParameters.getUri(), preliminaryRetargetingParameters.getPromotionParameter())));
                    PreliminaryRetargetingParameters.ValidateResult validated = preliminaryRetargetingParameters.validate().get();
                    Log.e(TAG, "Preliminary was valid: " + validated.isValid());
                } else {
                    Log.e(TAG, "No preliminary parameters");
                }
            } catch (ExecutionException | InterruptedException e) {
                MainActivity.this.runOnUiThread(() -> binding.retargetingTextView.setText(e.getMessage()));
                Log.e(TAG, "error during preliminary retargeting", e);
            }
            try {
                @Nullable RetargetingParameters retargetingParameters = MainApplication.sdk.getRetargetingParameters().get();
                MainActivity.this.runOnUiThread(() -> {
                    if (retargetingParameters == null) {
                        binding.retargetingTextView.setText(R.string.no_retargeting_parameters);
                    } else {
                        binding.retargetingTextView.setText(MessageFormat.format("uri: {0}\npromotion code: {1}", retargetingParameters.getUri(), retargetingParameters.getPromotionParameter()));
                        if ("thisIsSooCool".equals(retargetingParameters.getPromotionParameter())) {
                            Toast.makeText(MainActivity.this, "You got COINS!", Toast.LENGTH_LONG).show();
                        }
                    }
                });
                if (retargetingParameters == null) {
                    Log.e(TAG, "No retargeting parameters");
                } else {
                    Log.e(TAG, "Retargeting uri: " + retargetingParameters.getUri());
                    Log.e(TAG, "Promotion code: " + retargetingParameters.getPromotionParameter());
                    for (Map.Entry<String, String> entry : retargetingParameters.getParameters().entrySet()) {
                        Log.e(TAG, "Retargeting parameter " + entry.getKey() + ": " + entry.getValue());
                    }
                }
            } catch (ExecutionException | InterruptedException  e) {
                MainActivity.this.runOnUiThread(() -> binding.retargetingTextView.setText(e.getMessage()));
                Log.e(TAG, "error during retargeting", e);
            }
        }).start();
    }

    private void onStopTrackingClick(View view) {
        assert view != null;
        MainApplication.sdk.stop();
        refreshIsTracking();
    }

    private void reFetchButtonClick(View view){
        assert view != null;
        displayAttribution(MainApplication.sdk);
    }

    private void anonymousClick(View view) {
        assert view != null;
        MainApplication.sdk.anonymize().registerCallback(new Callback<Boolean>() {
            @Override
            public void resolve(Boolean response) {
                Log.e(TAG, "anonymous complete" );
            }

            @Override
            public void reject(@NonNull Throwable exception) {
                Log.e(TAG, "anonymous incomplete", exception);

            }
        });
    }

    private void experimentClick(View view) {
        assert view != null;
        Intent intent = new Intent(this, ExperimentActivity.class);
        startActivity(intent);
    }

    private void withAdvertiserId(AdvertiserIdCallback callback) throws SdkNotTrackingException{
        if (MainApplication.sdk == null) {
            callback.accept("");
            return;
        }

        MainApplication.sdk.getAdvertiserIdInfo().registerCallback(new Callback<AdvertiserIdInfo>() {
            @Override
            public void resolve(AdvertiserIdInfo response) {
                String advertiserId = response.getAdvertiserId();
                if (advertiserId == null) {
                    advertiserId = "";
                }

                callback.accept(advertiserId);
            }

            @Override
            public void reject(@NonNull Throwable exception) {
                Log.e(TAG, "Failed to read advertiser id", exception);
                callback.accept("");
            }
        });
    }

    interface AdvertiserIdCallback {
        void accept(@NonNull String advertiserId);
    }

    private void performIntentClick(String scheme, String host, String path) {
        try {
            withAdvertiserId(advertiserId -> {
                String clickId = UUID.randomUUID().toString().replaceAll("-", "");
                String url = "https://tracking.marketing-sandbox.info/click" +
                        "?appBundleId=io.justtrack.testapp" +
                        "&network=2327_justtest" +
                        "&sourceCampaignId=intentTest123" +
                        "&platform=android" +
                        "&clickId=" + clickId +
                        "&mobileId=" + advertiserId +
                        "&intentScheme=" + Uri.encode(scheme) +
                        "&intentHost=" + Uri.encode(host) +
                        "&intentPath=" + Uri.encode(path);

                Log.i(TAG, "Opening URL " + url);

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            });
        } catch (SdkNotTrackingException exception) {
            Log.e(TAG, "performIntentClick: " + exception );
        }
    }

    private void performRetargetingClick(String appId, String campaignHash, String externalCampaignId) {
        try {
            withAdvertiserId(advertiserId -> {
                String clickId = UUID.randomUUID().toString().replaceAll("-", "");
                String url = "https://tracking.marketing-sandbox.info/click" +
                        "?appId=" + appId +
                        "&bidtype=cpi" +
                        "&bidValue=1.42" +
                        "&country=DE" +
                        "&creativeId=999888" +
                        "&creativeName=TestApp_Internal_button" +
                        "&clickId=" + clickId +
                        "&hash=" + campaignHash +
                        "&externalCampaignId=" + externalCampaignId +
                        "&mobileId=" + advertiserId +
                        "&network=962_ironsource" +
                        "&platform=android" +
                        "&publisher=04bf1a65ee094adfa22f6759670b742a" +
                        "&sub0=sdkSub0" +
                        "&sub1=sdkSub1" +
                        "&sub2=sdkSub2" +
                        "&sub3=sdkSub3" +
                        "&sourceId=b52a68776d1149318c69664daa519955" +
                        "&subAppId=sdkSubAppId";

                Log.i(TAG, "Opening URL " + url);

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            });
        } catch (SdkNotTrackingException exception) {
            Log.e(TAG, "performRetargetingClick: ", exception);
        }

    }
}
