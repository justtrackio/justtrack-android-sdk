# Move all classes into our package (don't move anything in the root package or packages a, b, c, ...)
-flattenpackagehierarchy "io.justtrack"
# Keep parameter names so that users of the SDK have an easier time using it.
-keepparameternames
# Keep important attributes for our users
-keepattributes NonNull
-keepattributes Nullable
-keepattributes Deprecated

# This is our only kotlin interface we export (for now), so we have to mark it here by hand:
-keep public interface io.justtrack.AsyncFuture {
    public *;
}
-keep class io.justtrack.CrashReporter{
    public native void registerListener(java.lang.String, java.lang.String);
    private void startANRDetector();
    private java.lang.Object waitMainThread(kotlin.coroutines.Continuation);
}

-keep public class io.justtrack.Metric {
    *;
}

-keep public interface io.justtrack.config.RemoteConfig { public *; }
-keep public class io.justtrack.config.Assignment { public *; }
-keepclassmembers class io.justtrack.config.Assignment { *; }
-keep public class io.justtrack.config.JusttrackRemoteConfigSettings { public *; }
-keepclassmembers class io.justtrack.config.JusttrackRemoteConfigSettings { *; }

-keep class io.justtrack.okhttp.** { *; }
-keep class io.justtrack.exceptions.SdkNotTrackingException
-keep class io.justtrack.exceptions.IntegrityException
-keep class io.justtrack.exceptions.AwaitingIdException

-keep interface io.justtrack.ApplicationVersion

-keep public interface io.justtrack.Version {
    public *;
}

-keep public interface io.justtrack.ApplicationVersion {
    public *;
}

-keep public interface io.justtrack.versions.SDKVersion {
    public *;
}

# This is require to make InstallReferrer with ShadowJar work:
-keep class com.google.android.finsky.externalreferrer.GetInstallReferrerService { *; }
-keep class com.google.android.finsky.externalreferrer.IGetInstallReferrerService { *; }


#This is our package private method that we want to keep for wrapper:
-keep class io.justtrack.BaseJustTrackSdk {
    protected void reportReactNativeCrash(java.lang.String, java.lang.String);
    protected void reportUnityCrash(java.lang.String, java.lang.String);
}

-keep class io.justtrack.IntegrationManager {
    public static void enableUnityJavaProxyDebugging();
}

-keep public class io.justtrack.attribution.** {
    public *;
}

-keep public class io.justtrack.events.** {
    public *;
}

-keep public class io.justtrack.ads.** {
    public *;
}

-keep public class io.justtrack.exceptions.** {
    public *;
}
-keep public class io.justtrack.deeplinks.** {
    public *;
}

-keep public class io.justtrack.integrations.** {
    public *;
}

-keep public class io.justtrack.log.** {
    public *;
}

-keep public class io.justtrack.retargeting.** {
    public *;
}

-keep public class io.justtrack.AppEvent {
    public *;
}

-keep public class io.justtrack.Callback {
    public *;
}

-keep public class io.justtrack.AttributionException {
    public *;
}

-keep public class io.justtrack.JustTrackSdk {
    public *;
}

-keep public class io.justtrack.InitializerContentProvider {
    public *;
}

-keep public class io.justtrack.AttributionException {
    public *;
}

-keep public class io.justtrack.AttributionListener {
    public *;
}

-keep class io.justtrack.JustTrack {
    public static *;
    public static final ** Companion;
}

-keep class io.justtrack.JustTrack$Companion {
    public *;
}


-keep public class io.justtrack.JustTrackSdkBuilder {
    public *;
}

-keep public class io.justtrack.SdkBuilder {
    public *;
}

-keep public class io.justtrack.Subscription {
    public *;
}

-keep public class io.justtrack.PlatformType {
    public *;
}

# Without this throws is not kept for some reason
-keepattributes Exceptions


-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn com.sun.jna.JNIEnv
-dontwarn com.sun.jna.Library
-dontwarn com.sun.jna.Native
-dontwarn com.sun.jna.NativeLibrary
-dontwarn com.sun.jna.Platform
-dontwarn java.lang.instrument.ClassDefinition
-dontwarn java.lang.instrument.IllegalClassFormatException
-dontwarn java.lang.instrument.UnmodifiableClassException
-dontwarn java.lang.invoke.StringConcatFactory