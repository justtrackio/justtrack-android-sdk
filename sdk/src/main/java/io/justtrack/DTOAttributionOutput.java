package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;

class DTOAttributionOutput {
    @NonNull
    private final DTOAttributionOutputUser user;
    @NonNull
    private final DTOAttributionOutputAttribution attribution;
    @Nullable
    private final DTOAttributionOutputRetargeting retargeting;
    @Nullable
    private final DTOAttributionOutputSdkConfig sdkConfig;

    DTOAttributionOutput(@NonNull DTOAttributionOutput obj) {
        this.user = obj.user;
        this.attribution = obj.attribution;
        this.retargeting = obj.retargeting;
        this.sdkConfig = obj.sdkConfig;
    }

    DTOAttributionOutput(
            @NonNull DTOAttributionOutputUser user,
            @NonNull DTOAttributionOutputAttribution attribution,
            @Nullable DTOAttributionOutputRetargeting retargeting,
            @Nullable DTOAttributionOutputSdkConfig sdkConfig) {
        this.user = user;
        this.attribution = attribution;
        this.retargeting = retargeting;
        this.sdkConfig = sdkConfig;
    }

    DTOAttributionOutput(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.user = new DTOAttributionOutputUser(obj.getJSONObject("user")); 
        this.attribution = new DTOAttributionOutputAttribution(obj.getJSONObject("attribution"), formatter); 

        if (obj.has("retargeting") && obj.get("retargeting") != JSONObject.NULL) {
            this.retargeting = new DTOAttributionOutputRetargeting(obj.getJSONObject("retargeting")); 
        } else {
            this.retargeting = null;
        }

        if (obj.has("sdkConfig") && obj.get("sdkConfig") != JSONObject.NULL) {
            this.sdkConfig = new DTOAttributionOutputSdkConfig(obj.getJSONObject("sdkConfig")); 
        } else {
            this.sdkConfig = null;
        }
    }

    @NonNull
    DTOAttributionOutputUser getUser() {
        return this.user;
    }

    @NonNull
    DTOAttributionOutputAttribution getAttribution() {
        return this.attribution;
    }

    @Nullable
    DTOAttributionOutputRetargeting getRetargeting() {
        return this.retargeting;
    }

    @Nullable
    DTOAttributionOutputSdkConfig getSdkConfig() {
        return this.sdkConfig;
    }
}