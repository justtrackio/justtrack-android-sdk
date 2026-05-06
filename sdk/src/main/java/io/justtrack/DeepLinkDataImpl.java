package io.justtrack;

import android.net.Uri;

import androidx.annotation.NonNull;

import io.justtrack.deeplinks.DeepLinkData;

class DeepLinkDataImpl implements DeepLinkData {
    private final @NonNull Uri uri;

    DeepLinkDataImpl(@NonNull Uri uri) {
        this.uri = uri;
    }

    @NonNull
    @Override
    public Uri getUri() {
        return uri;
    }
}
