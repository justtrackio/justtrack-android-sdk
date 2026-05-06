package io.justtrack;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Browser;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IntentsTest {
    private static final String EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url";
    private static final String MARKET_INTENT_URI_PACKAGE_PREFIX = "market://details?id=";
    private static final Set<String> ALWAYS_DENY_SCHEMES = new HashSet<>(Arrays.asList("jar", "file", "javascript", "data", "about"));
    private static final Set<String> ENGINE_SUPPORTED_SCHEMES = new HashSet<>(Arrays.asList("about", "data", "file", "ftp", "http",
            "https", "moz-extension", "moz-safe-about", "resource", "view-source", "ws", "wss", "blob"));

    @Test
    public void intentTest() {
        boolean includeHttpAppLinks = false;
        boolean ignoreDefaultBrowser = false;
        boolean launchInApp = false;

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String url = "intent://mobileapp.com/open#Intent;"
                + "scheme=mobileapp;"
                + "action=android.intent.action.VIEW;"
                + "package=online.mobile.app;"
                + "S.browser_fallback_url=https%3A%2F%2Fdetails%3Fid%3Donline.awesome.app%26referrer%3D836176c9-807b-45b5-4832-05ce36abc4c1;end";

        RedirectData redirectData = createBrowsableIntents(context, url);

        boolean isAppIntentHttpOrHttps = redirectData.appIntent != null
                && redirectData.appIntent.getData() != null
                && redirectData.appIntent.getData().getScheme().startsWith("http");

        boolean isEngineSupportedScheme = ENGINE_SUPPORTED_SCHEMES.contains(Uri.parse(url).getScheme());

        //noinspection ConstantConditions
        Intent appIntent = redirectData.resolveInfo == null && isEngineSupportedScheme
                ? null
                : redirectData.resolveInfo == null && redirectData.marketplaceIntent != null
                ? null
                : includeHttpAppLinks
                && (ignoreDefaultBrowser || (redirectData.appIntent != null && isDefaultBrowser(context, redirectData.appIntent)))
                ? null
                : includeHttpAppLinks && isAppIntentHttpOrHttps
                ? redirectData.appIntent
                : !launchInApp && ENGINE_SUPPORTED_SCHEMES.contains(Uri.parse(url).getScheme())
                ? null
                : redirectData.appIntent;

        String fallbackUrl = (redirectData.fallbackIntent != null
                && redirectData.fallbackIntent.getData() != null
                && redirectData.fallbackIntent.getData().getScheme().startsWith("http")
        ) ? redirectData.fallbackIntent.getDataString() : null;

        // no need to check marketplace intent since it is only set if a package is set in the intent
        AppLinkRedirect appLinkRedirect = new AppLinkRedirect(appIntent, fallbackUrl, redirectData.marketplaceIntent);

        Log.i("INTENT_TEST", appLinkRedirect.appIntent != null ? appLinkRedirect.appIntent.toString() : "null");
        Log.i("INTENT_TEST", appLinkRedirect.fallbackUrl != null ? appLinkRedirect.fallbackUrl : "null");
        Log.i("INTENT_TEST", appLinkRedirect.marketplaceIntent != null ? appLinkRedirect.marketplaceIntent.toString() : "null");
    }

    private RedirectData createBrowsableIntents(Context context, String url) {
        boolean includeInstallAppFallback = true;
        boolean isInstalled = false;

        Intent intent = safeParseUri(context, url, Intent.URI_INTENT_SCHEME);
        Log.i("INTENT_TEST", intent != null ? intent.toString() : "null");
        if (intent != null) {
            Log.i("INTENT_TEST", intent.getExtras() != null ? intent.getExtras().toString() : "null");
        }

        //noinspection ConstantConditions
        Intent marketplaceIntent = intent != null && intent.getPackage() != null ? (
                includeInstallAppFallback && !isInstalled ? safeParseUri(context, MARKET_INTENT_URI_PACKAGE_PREFIX + intent.getPackage(), 0) : null
        ) : null;

        if (marketplaceIntent != null) {
            marketplaceIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }

        Intent appIntent = intent == null || intent.getData() == null
                ? null
                : ALWAYS_DENY_SCHEMES.contains(intent.getData().getScheme()) ? null : intent;

        if (appIntent != null) {
            appIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            appIntent.setComponent(null);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (appIntent.getSelector() != null) {
                appIntent.getSelector().addCategory(Intent.CATEGORY_BROWSABLE);
                appIntent.getSelector().setComponent(null);
            }
            appIntent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        }

        List<ResolveInfo> resolveInfoList = appIntent == null ? null : findActivities(context, appIntent).stream().filter(it ->
                it.filter != null && !(it.filter.countDataPaths() == 0 && it.filter.countDataAuthorities() == 0)
        ).collect(Collectors.toList());
        ResolveInfo resolveInfo = resolveInfoList == null ? null : resolveInfoList.stream().findFirst().orElse(null);

        // only target intent for specific app if only one non browser app is found
        if (resolveInfoList != null && resolveInfoList.size() == 1) {
            appIntent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
        }

        Intent fallbackIntent = (intent != null
                && intent.getStringExtra(EXTRA_BROWSER_FALLBACK_URL) != null
        ) ? safeParseUri(context, intent.getStringExtra(EXTRA_BROWSER_FALLBACK_URL), 0) : null;

        return new RedirectData(appIntent, fallbackIntent, marketplaceIntent, resolveInfo);
    }

    List<ResolveInfo> findActivities(Context context, Intent intent) {
        return context.getPackageManager().queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);
    }

    @Nullable
    ResolveInfo findDefaultActivity(Context context, Intent intent) {
        return context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
    }

    boolean isDefaultBrowser(Context context, Intent intent) {
        ResolveInfo resolveInfo = findDefaultActivity(context, intent);

        return resolveInfo != null
                && resolveInfo.activityInfo != null
                && resolveInfo.activityInfo.packageName.equals(context.getPackageName());
    }

    @Nullable
    Intent safeParseUri(Context context, String uri, int flags) {
        try {
            Intent intent = Intent.parseUri(uri, flags);
            if (context.getPackageName() != null && intent != null && context.getPackageName().equals(intent.getPackage())) {
                // Ignore intents that would open in the browser itself
                return null;
            } else {
                return intent;
            }
        } catch (URISyntaxException | NumberFormatException exception) {
            Log.e("INTENT_TEST", uri, exception);
            return null;
        }
    }

    private static class RedirectData {
        private final @Nullable Intent appIntent;
        private final @Nullable Intent fallbackIntent;
        private final @Nullable Intent marketplaceIntent;
        private final @Nullable ResolveInfo resolveInfo;

        private RedirectData(
                @Nullable Intent appIntent,
                @Nullable Intent fallbackIntent,
                @Nullable Intent marketplaceIntent,
                @Nullable ResolveInfo resolveInfo
        ) {
            this.appIntent = appIntent;
            this.fallbackIntent = fallbackIntent;
            this.marketplaceIntent = marketplaceIntent;
            this.resolveInfo = resolveInfo;
        }
    }

    private static class AppLinkRedirect {
        private final @Nullable Intent appIntent;
        private final @Nullable String fallbackUrl;
        private final @Nullable Intent marketplaceIntent;

        private AppLinkRedirect(@Nullable Intent appIntent, @Nullable String fallbackUrl, @Nullable Intent marketplaceIntent) {
            this.appIntent = appIntent;
            this.fallbackUrl = fallbackUrl;
            this.marketplaceIntent = marketplaceIntent;
        }

        /**
         * If there is a third-party app intent.
         */
        boolean hasExternalApp() {
            return appIntent != null;
        }

        /**
         * If there is a fallback URL (in case the intent fails).
         */
        boolean hasFallback() {
            return fallbackUrl != null;
        }

        /**
         * If there is a marketplace intent (in case the external app is not installed).
         */
        boolean hasMarketplaceIntent() {
            return marketplaceIntent != null;
        }

        /**
         * If the app link is a redirect (to an app or URL).
         */
        boolean isRedirect() {
            return hasExternalApp() || hasFallback() || hasMarketplaceIntent();
        }

        /**
         * Is the app link one that can be installed from a store.
         */
        boolean isInstallable() {
            return appIntent != null && appIntent.getData() != null && "market".equals(appIntent.getData().getScheme());
        }
    }
}
