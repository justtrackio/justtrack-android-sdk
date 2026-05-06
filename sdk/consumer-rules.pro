# Prevent byte buddy from breaking (see also https://github.com/Guardsquare/proguard/issues/238)
-keepclassmembers class com.android.dx.dex.cf.CfTranslator { public static *** translate(...); }

# Keep Adjoe for IntegrationManager
-keep class io.adjoe.programmatic.sdk.AdjoeProgrammatic {*;}
-keep interface io.adjoe.programmatic.sdk.AdjoeImpressionDataListener {*;}
-keep class io.adjoe.wave.sdk.AdjoeProgrammatic {*;}
-keep interface io.adjoe.wave.sdk.AdjoeImpressionDataListener {*;}

# Keep AppLovin for IntegrationManager
-keep class com.applovin.sdk.AppLovinSdk { private static *** getVersion(...); }
-keep class com.applovin.adview.AppLovinFullscreenActivity

# Keep AppLovin MAX for IntegrationManager
-keep class com.applovin.sdk.AppLovinSdk {
    public static *** getInstance(...);
    public *** setUserIdentifier(...);
}
-keep class com.applovin.communicator.AppLovinCommunicator {
    public static *** getInstance(...);
    public *** subscribe(...);
    public *** unsubscribe(...);
}
-keep class com.applovin.communicator.AppLovinCommunicatorMessage {
    public *** getMessageData(...);
}
-keep class com.applovin.communicator.AppLovinCommunicatorSubscriber {
    public *** onMessageReceived(...);
    public *** getCommunicatorId(...);
}

# Keep Chartbooster for IntegrationManager
-keep class com.chartboost.sdk.Chartboost { public static *** getSDKVersion(...); }
# For version 9
-keep class com.chartboost.sdk.view.CBImpressionActivity
# For version 8
-keep class com.chartboost.sdk.CBImpressionActivity

# Keep Unity Ads for IntegrationManager
-keep class com.unity3d.ads.UnityAds {*;}
-keep class com.unity3d.ads.UnityAds$UnityAdsShowCompletionState {*;}
-keep class com.unity3d.services.ads.operation.show.IShowModule {*;}
-keep class com.unity3d.services.ads.operation.show.IShowOperation {*;}
-keep class com.unity3d.services.ads.operation.show.ShowModule {*;}
-keep class com.unity3d.services.ads.operation.show.ShowModuleDecoratorTimeout {*;}
-keep class com.unity3d.services.ads.operation.show.ShowOperation {*;}
-keep class com.unity3d.services.ads.operation.show.ShowOperationState {*;}
-keep class com.unity3d.services.banners.BannerView {*;}
-keep class com.unity3d.services.banners.BannerViewCache {*;}
-keep class com.unity3d.services.core.cache.CacheDirectory {*;}
-keep class com.unity3d.services.core.properties.ClientProperties {*;}

# Keep IronSource for IntegrationManager
-keep class com.ironsource.mediationsdk.IronSource {*;}
-keep class com.ironsource.mediationsdk.impressionData.ImpressionData {*;}
-keep interface com.ironsource.mediationsdk.impressionData.ImpressionDataListener {*;}

# Keep Firebase for IntegrationManager
-keep class com.google.firebase.analytics.FirebaseAnalytics {*;}
-keep interface com.google.android.gms.tasks.OnCompleteListener {*;}
-keep class com.google.android.gms.tasks.Task {*;}

# Keep ReflectionHelper for Unity3D debugging
-keep class com.unity3d.player.ReflectionHelper {*;}

# Keep BillingClient for IAP tracking
-keep class com.android.billingclient.api.BillingClient {*;}
-keep class com.android.billingclient.BuildConfig{*;}
-keep interface com.android.billingclient.api.PurchasesUpdatedListener{*;}
-keep interface com.android.billingclient.api.BillingClientStateListener{*;}
-keep class com.android.billingclient.api.Purchase{*;}
-keep class com.android.billingclient.api.BillingResult{*;}
-keep class com.android.billingclient.api.SkuDetailsResponseListener{*;}
-keep class com.android.billingclient.api.SkuDetails{*;}
-keep class com.android.billingclient.api.SkuDetailsParams{*;}
-keep class com.android.billingclient.api.ProductDetailsResponseListener{*;}
-keep class com.android.billingclient.api.QueryProductDetailsParams{*;}
-keep class com.android.billingclient.api.ProductDetails{*;}

-keep class io.justtrack.BillingTrackerImpl {*;}
-keep class * extends com.android.billingclient.api.BillingClient {*;}

# Copy of our rules again to keep the public API of the SDK for the Unity3D SDK

# Keep all public interfaces
-keep public interface io.justtrack.** {*;}
# Keep all generated public classes
-keep public class io.justtrack.** {*;}
# Keep public methods inside non-public classes
-keepclassmembers class io.justtrack.** {
    public <methods>;
}

# Prevent R8 from moving SDK classes into different packages. The SDK contains many
# package-private Java classes that access each other. If R8 repackages them into different
# packages (e.g. via -flattenpackagehierarchy or -repackageclasses), package-private access
# breaks at runtime with IllegalAccessError.
-keeppackagenames io.justtrack.**
-keepnames class io.justtrack.**

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
# May be used with robolectric or deliberate use of Bouncy Castle on Android
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**