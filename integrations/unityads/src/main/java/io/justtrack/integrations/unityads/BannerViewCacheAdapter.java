package io.justtrack.integrations.unityads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.justtrack.JustTrackSdk;
import io.justtrack.ads.AdImpression;
import io.justtrack.ads.AdUnit;
import io.justtrack.log.Logger;

public class BannerViewCacheAdapter {
    @Nullable
    static Class<?> bannerViewCacheClass;
    @Nullable
    private static Object wrappedInstance;
    @Nullable
    private static Method loadMethod;
    private static final List<Map<String, WeakReference<Object>>> bannerViewsMaps = new ArrayList<>();
    @Nullable
    static WeakReference<JustTrackSdk> sdk;
    @Nullable
    static WeakReference<Logger> logger;

    public static void init(@This @NonNull Object self, @Argument(0) @NonNull Object wrappedInstance) {
        BannerViewCacheAdapter.wrappedInstance = wrappedInstance;
        if (bannerViewCacheClass != null) {
            try {
                // share the same map between the wrapper instance and the original instance
                // also copy over all other fields we might need
                for (Field field : bannerViewCacheClass.getDeclaredFields()) {
                    if ((field.getModifiers() & Modifier.STATIC) != 0) {
                        continue;
                    }
                    final boolean wasAccessible = field.isAccessible();
                    field.setAccessible(true);
                    Object value = field.get(wrappedInstance);
                    // save the map to lookup banner ids
                    if (value instanceof Map) {
                        //noinspection unchecked
                        bannerViewsMaps.add((Map<String, WeakReference<Object>>) value);
                    }
                    field.set(self, value);
                    field.setAccessible(wasAccessible);
                }
                // find our load method so we can invoke it later
                loadMethod = bannerViewCacheClass.getMethod("triggerBannerLoadEvent", String.class);
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    public static void triggerBannerLoadEvent(@Argument(0) @Nullable String bannerAdId) {
        for (Map<String, WeakReference<Object>> map: bannerViewsMaps) {
            if (bannerAdId != null) {
                if (map != null) {
                    WeakReference<Object> ref = map.get(bannerAdId);
                    if (ref != null) {
                        Object bannerView = ref.get();
                        if (bannerView != null) {
                            reportBannerView(bannerView);
                            break;
                        }
                    }
                }
            }
        }

        if (loadMethod != null && wrappedInstance != null) {
            try {
                loadMethod.invoke(wrappedInstance, bannerAdId);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    private static void reportBannerView(@NonNull Object bannerView) {
        if (sdk == null) {
            return;
        }
        JustTrackSdk sdkRef = sdk.get();
        if (sdkRef == null) {
            return;
        }

        try {
            Method getPlacementId = bannerView.getClass().getMethod("getPlacementId");
            String placementId = (String) getPlacementId.invoke(bannerView);
            sdkRef.forwardAdImpression(new AdImpression(
                    AdUnit.Banner,
                    "unity",
                    "unity",
                    placementId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        } catch (Throwable exception) {
            if (logger != null) {
                final Logger loggerRef = logger.get();
                loggerRef.error("Failed to handle Unity banner event", exception);
            }
        }
    }
}
