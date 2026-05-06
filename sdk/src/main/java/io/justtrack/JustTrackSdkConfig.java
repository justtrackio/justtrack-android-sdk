package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.justtrack.exceptions.InvalidFieldException;

class JustTrackSdkConfig {
    @Nullable
    final String userId;
    @Nullable
    final String trackingId;
    @NonNull
    final String trackingIdProvider;
    @Nullable
    final String firebaseAppInstanceId;

    final boolean automaticIAPTracking;

    JustTrackSdkConfig(
            @Nullable String userId,
            @Nullable String trackingId,
            @NonNull String trackingIdProvider,
            @Nullable String firebaseAppInstanceId, 
            boolean automaticIAPTracking
    ) {
        this.userId = userId;
        this.trackingId = trackingId;
        this.trackingIdProvider = trackingIdProvider;
        this.firebaseAppInstanceId = firebaseAppInstanceId;
        this.automaticIAPTracking = automaticIAPTracking;
    }

    static class Builder {
        @Nullable
        private String userId = null;
        @Nullable
        private String trackingId = null;
        @NonNull
        private String trackingIdProvider = "advertiserId";
        @Nullable
        private String firebaseAppInstanceId = null;

        private boolean automaticIAPTracking = true;

        /**
         * Forward a user id to the justtrack backend upon SDK init.
         *
         * <p>The user id must be shorter than 4096 characters and consist only of printable ASCII
         * characters (U+0020 to U+007E).
         *
         * @param userId The user id to forward.
         * @return The builder so you can chain methods if you want.
         * @throws InvalidFieldException If the user id was set to an invalid value.
         */
        Builder withUserId(@NonNull String userId) throws InvalidFieldException {
            if (!Validation.validUserId(userId)) {
                throw new InvalidFieldException("userId", userId, 1, 4096, "ASCII");
            }

            this.userId = userId;

            return this;
        }

        /**
         * Forward the Firebase app instance id (<a href="https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics#public-taskstring-getappinstanceid">how to obtain one</a>)
         * to the justtrack backend upon SDK init.
         *
         * <p>The Firebase app instance id must be between 8 and 256 characters and consist only of printable ASCII
         * characters (U+0020 to U+007E).
         *
         * @param firebaseAppInstanceId The id to forward.
         * @return The builder so you can chain methods if you want.
         * @throws InvalidFieldException If the Firebase app instance id was set to an invalid value.
         */
        Builder withFirebaseIntegration(@NonNull String firebaseAppInstanceId) throws InvalidFieldException {
            if (!Validation.validFirebaseAppInstanceId(firebaseAppInstanceId)) {
                throw new InvalidFieldException("firebaseAppInstanceId", firebaseAppInstanceId, 8, 256, "ASCII");
            }

            this.firebaseAppInstanceId = firebaseAppInstanceId;

            return this;
        }

        /**
         * Set the tracking id the SDK will send to the backend.
         *
         * <p>The tracking id and provider must be shorter than 4096 characters and consist only of printable ASCII
         * characters (U+0020 to U+007E).
         *
         * @param trackingId       The tracking id the SDK will send to the backend.
         * @param trackingProvider The tracking provider which supplied the trackingId.
         * @return The builder so you can chain methods if you want.
         * @throws InvalidFieldException If the tracking id or tracking provider were set to invalid values.
         */
        Builder withTrackingId(@Nullable String trackingId, @NonNull String trackingProvider) throws InvalidFieldException {
            if (trackingId != null && !Validation.validTrackingId(trackingId)) {
                throw new InvalidFieldException("trackingId", trackingId, 4096, "ASCII");
            }

            if (!Validation.validTrackingProvider(trackingProvider)) {
                throw new InvalidFieldException("trackingProvider", trackingProvider, 4096, "ASCII");
            }

            this.trackingId = trackingId;
            if (TextUtils.isNullOrEmpty(trackingId)) {
                this.trackingIdProvider = "advertiserId";
            } else {
                this.trackingIdProvider = trackingProvider;
            }

            return this;
        }

        /**
         * The justtrack SDK can automatically track in-app product and subscription purchases and
         * forward them to the justtrack backend. It is enabled by default, but this method allows you
         * to configure the automation for your needs.
         *
         * @param enabled Set this to true to automatically forward in-app product and subscription purchases.
         * @return The builder so you can chain methods if you want.
         */
        Builder withAutomaticInAppPurchaseTracking(boolean enabled) {
            this.automaticIAPTracking = enabled;

            return this;
        }

        /**
         * Build a new instance of the config. You should not use the builder after calling this method.
         *
         * @return A new instance of the config.
         */
        @NonNull
        JustTrackSdkConfig build() {
            return new JustTrackSdkConfig(
                    userId,
                    trackingId,
                    trackingIdProvider,
                    firebaseAppInstanceId,
                    automaticIAPTracking
            );
        }
    }
}
