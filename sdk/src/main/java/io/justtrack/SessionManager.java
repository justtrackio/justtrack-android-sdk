package io.justtrack;

import androidx.annotation.NonNull;

interface SessionManager {
    default void start(@NonNull BaseJustTrackSdk sdk) {
    }

    default void shutdown(@NonNull BaseJustTrackSdk sdk) {
    }

    @NonNull
    String getLatestSessionId();

    default void onResume() {
    }

    default void onPause() {
    }

    default void updateSessionTimeStamp() {
    }
}
