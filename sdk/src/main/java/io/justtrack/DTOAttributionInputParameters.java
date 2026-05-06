package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionInputParameters implements JSONEncodable {
    @Nullable
    private final String appleSearchAdsToken;
    @Nullable
    private final String installSource;
    @Nullable
    private final String gbraid;
    @Nullable
    private final String integritySecret;

    DTOAttributionInputParameters(@NonNull DTOAttributionInputParameters obj) {
        this.appleSearchAdsToken = obj.appleSearchAdsToken;
        this.installSource = obj.installSource;
        this.gbraid = obj.gbraid;
        this.integritySecret = obj.integritySecret;
    }

    DTOAttributionInputParameters(
            @Nullable String appleSearchAdsToken,
            @Nullable String installSource,
            @Nullable String gbraid,
            @Nullable String integritySecret) {
        this.appleSearchAdsToken = appleSearchAdsToken;
        this.installSource = installSource;
        this.gbraid = gbraid;
        this.integritySecret = integritySecret;
    }

    DTOAttributionInputParameters(@NonNull JSONObject obj) throws JSONException {

        if (obj.has("appleSearchAdsToken") && obj.get("appleSearchAdsToken") != JSONObject.NULL) {
            this.appleSearchAdsToken = obj.getString("appleSearchAdsToken");
        } else {
            this.appleSearchAdsToken = null;
        }

        if (obj.has("installSource") && obj.get("installSource") != JSONObject.NULL) {
            this.installSource = obj.getString("installSource");
        } else {
            this.installSource = null;
        }

        if (obj.has("gbraid") && obj.get("gbraid") != JSONObject.NULL) {
            this.gbraid = obj.getString("gbraid");
        } else {
            this.gbraid = null;
        }

        if (obj.has("integritySecret") && obj.get("integritySecret") != JSONObject.NULL) {
            this.integritySecret = obj.getString("integritySecret");
        } else {
            this.integritySecret = null;
        }
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();

        if (appleSearchAdsToken != null) {
            obj.put("appleSearchAdsToken", this.appleSearchAdsToken);
        } else {
            obj.put("appleSearchAdsToken", JSONObject.NULL);
        }

        if (installSource != null) {
            obj.put("installSource", this.installSource);
        } else {
            obj.put("installSource", JSONObject.NULL);
        }

        if (gbraid != null) {
            obj.put("gbraid", this.gbraid);
        } else {
            obj.put("gbraid", JSONObject.NULL);
        }

        if (integritySecret != null) {
            obj.put("integritySecret", this.integritySecret);
        } else {
            obj.put("integritySecret", JSONObject.NULL);
        }
        return obj;
    }
}