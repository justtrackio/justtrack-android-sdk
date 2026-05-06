package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONObject;

interface NamedDatum {
    @NonNull
    String getDatum();

    @NonNull
    JSONObject getDimensions();
}
