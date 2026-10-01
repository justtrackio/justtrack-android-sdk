package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.justtrack.events.Dimension;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
class Validation {
    static boolean validTrackingId(@NonNull String trackingId) {
        return trackingId.length() < 4096 && TextUtils.isASCII(trackingId);
    }

    static boolean validTrackingProvider(@NonNull String trackingProvider) {
        return trackingProvider.length() < 4096 && TextUtils.isASCII(trackingProvider);
    }

    static boolean validEventName(@NonNull String value) {
        return value.length() < 256 && TextUtils.isISO88591(value);
    }

    static boolean validDimensionName(@NonNull String dimension) {
        return dimension.length() < 256 && dimension.matches("^[a-z0-9_]+$");
    }

    static boolean validDimensionValue(@NonNull String dimension, @Nullable String value) {
        if (dimension.equals(Dimension.JT_TOKEN.toString())) {
            return true;
        }

        if (value == null) {
            return true;
        }

        return value.length() < 4096 && TextUtils.isISO88591(value);
    }

    static boolean validUserId(@NonNull String userId) {
        return !userId.isEmpty() && userId.length() < 4096 && TextUtils.isASCII(userId);
    }

    static boolean validFirebaseAppInstanceId(@NonNull String firebaseAppInstanceId) {
        return firebaseAppInstanceId.length() >= 8 && firebaseAppInstanceId.length() < 256 && TextUtils.isASCII(firebaseAppInstanceId);
    }

    static boolean validCommonInput(@NonNull String input, int maxLength) {
        return input.length() < maxLength && TextUtils.isASCII(input) && !TextUtils.isNullOrEmpty(input);
    }
}
