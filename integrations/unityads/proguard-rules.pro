-keep public class io.justtrack.integrations.unityads.UnityAdsIntegrationAdapter {
    *;
}
-keep class io.justtrack.integrations.unityads.UnityAdsIntegrationAdapter$* {
    *;
}

-keepnames class io.justtrack.integrations.unityads.UnityAdsIntegrationAdapter {
    *;
}

-keep class io.justtrack.integrations.unityads.BannerViewCacheAdapter {
    *;
}

-keepnames class io.justtrack.integrations.unityads.BannerViewCacheAdapter {
    *;
}

-keep class com.android.dx.** { *; }
-keeppackagenames com.android.dx.**
-keepnames class com.android.dx.**

-keep class net.bytebuddy.** { *; }
-keepnames class net.bytebuddy.**
-keeppackagenames net.bytebuddy.**
-keep class net.bytebuddy.android.AndroidClassLoadingStrategy$DexProcessor$** { *; }
-keepnames class net.bytebuddy.android.AndroidClassLoadingStrategy$DexProcessor$**

-keep class io.justtrack.integrations.unityads.BannerViewCacheAdapter { *; }

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature

-keep class com.android.dx.dex.cf.CfTranslator { *; }
-keep class com.android.dx.cf.direct.DirectClassFile { *; }
-keep class com.android.dx.dex.cf.CfOptions { *; }
-keep class com.android.dx.dex.DexOptions { *; }
-keep class com.android.dx.dex.file.DexFile { *; }