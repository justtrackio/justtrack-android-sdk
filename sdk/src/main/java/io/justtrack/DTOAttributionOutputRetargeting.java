package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

class DTOAttributionOutputRetargeting {
    @NonNull
    private final String url;
    @NonNull
    private final Map<String, String> attributes;

    DTOAttributionOutputRetargeting(@NonNull DTOAttributionOutputRetargeting obj) {
        this.url = obj.url;
        this.attributes = obj.attributes;
    }

    DTOAttributionOutputRetargeting(
            @NonNull String url,
            @NonNull Map<String, String> attributes) {
        this.url = url;
        this.attributes = attributes;
    }

    DTOAttributionOutputRetargeting(@NonNull JSONObject obj) throws JSONException {
        this.url = obj.getString("url");
        attributes = new HashMap<>();
        JSONObject attributesJsonObject = obj.getJSONObject("attributes");
        Iterable<String> attributesIterable = attributesJsonObject::keys;
        for (String key : attributesIterable) {
            String value = attributesJsonObject.getString(key);
            attributes.put(key, value);
        }
    }

    @NonNull
    String getUrl() {
        return this.url;
    }

    @NonNull
    Map<String, String> getAttributes() {
        return this.attributes;
    }
}