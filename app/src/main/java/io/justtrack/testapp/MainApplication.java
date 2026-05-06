package io.justtrack.testapp;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.justtrack.JustTrackSdk;
import io.justtrack.deeplinks.DeepLinkHandled;
import io.justtrack.testapp.database.SQLiteDatabaseHelper;

public class MainApplication extends Application {
    private static final String TAG = "JustTrackSDK Test App";
    @Nullable
    public static JustTrackSdk sdk = null;
    @Nullable
    public static String customUserId = null;
    @Nullable
    public static SQLiteDatabaseHelper databaseHelper;
    public static final MutableLiveData<Boolean> isSdkTracking = new MutableLiveData<>(false);

    @Override
    public void onCreate() {
        super.onCreate();
        enableStrictMode();
        databaseHelper = new SQLiteDatabaseHelper(this);
    }

    // Deadlock-Safety: This is a test app
    public static synchronized void initSdk(Context context, JustTrackSdk sdk) {
        if (MainApplication.sdk != null) {
            return;
        }
        MainApplication.sdk = sdk;

        sdk.registerDeepLinkListener(deepLink -> {
            Log.i(TAG, "Handled new deeplink with URI " + deepLink.getUri());
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, deepLink.getUri().toString(), Toast.LENGTH_SHORT).show());

            return DeepLinkHandled.DEEP_LINK_HANDLED;
        });
    }

    public static long getStoredLogCount() {
        if (databaseHelper == null) {
            return 0L;
        }

        return databaseHelper.getStoredLogCount();
    }

    private void enableStrictMode() {
        //Do not use penaltyDeath(), some phone model will encounter Disk-Read on first application onCreate().
        StrictMode.ThreadPolicy.Builder threadPolicyBuilder = new StrictMode.ThreadPolicy.Builder().detectAll();
        StrictMode.VmPolicy.Builder vmPolicyBuilder = new StrictMode.VmPolicy.Builder().detectAll();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vmPolicyBuilder.detectNonSdkApiUsage();
            ExecutorService executor = Executors.newSingleThreadExecutor();

            threadPolicyBuilder = threadPolicyBuilder.penaltyListener(executor, this::reportViolation);
            vmPolicyBuilder = vmPolicyBuilder.penaltyListener(executor, this::reportViolation);
        } else {
            threadPolicyBuilder = threadPolicyBuilder.penaltyLog();
            vmPolicyBuilder = vmPolicyBuilder.penaltyLog();
        }

        StrictMode.setThreadPolicy(threadPolicyBuilder.build());
        StrictMode.setVmPolicy(vmPolicyBuilder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void reportViolation(@NonNull Violation violation) {
        Log.e("JustTrackSDK Test App", "Strict mode violation", violation);
    }
}
