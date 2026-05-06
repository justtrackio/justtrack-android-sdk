package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;

class DTOAttributionInput implements JSONEncodable {
    @NonNull
    private final DTOAppVersion appVersion;
    @NonNull
    private final DTOSdkVersion sdkVersion;
    @NonNull
    private final DTOAttributionInputUser user;
    @NonNull
    private final DTOAttributionInputDevice device;
    @NonNull
    private final Iterable<String> claims;
    @NonNull
    private final DTOAttributionInputParameters parameters;
    @Nullable
    private final DTOAttributionInputReferrer referrer;

    DTOAttributionInput(@NonNull DTOAttributionInput obj) {
        this.appVersion = obj.appVersion;
        this.sdkVersion = obj.sdkVersion;
        this.user = obj.user;
        this.device = obj.device;
        this.claims = obj.claims;
        this.parameters = obj.parameters;
        this.referrer = obj.referrer;
    }

    DTOAttributionInput(
            @NonNull DTOAppVersion appVersion,
            @NonNull DTOSdkVersion sdkVersion,
            @NonNull DTOAttributionInputUser user,
            @NonNull DTOAttributionInputDevice device,
            @NonNull Iterable<String> claims,
            @NonNull DTOAttributionInputParameters parameters,
            @Nullable DTOAttributionInputReferrer referrer) {
        this.appVersion = appVersion;
        this.sdkVersion = sdkVersion;
        this.user = user;
        this.device = device;
        this.claims = claims;
        this.parameters = parameters;
        this.referrer = referrer;
    }

    DTOAttributionInput(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.appVersion = new DTOAppVersion(obj.getJSONObject("appVersion"));
        this.sdkVersion = new DTOSdkVersion(obj.getJSONObject("sdkVersion")); 
        this.user = new DTOAttributionInputUser(obj.getJSONObject("user"));
        this.device = new DTOAttributionInputDevice(obj.getJSONObject("device"));
        ArrayList<String> claimsList = new ArrayList<>();
        if (obj.has("claims")) {
            JSONArray claimsJsonArray = obj.getJSONArray("claims");
            for (int rulesIndex = 0; rulesIndex < claimsJsonArray.length(); rulesIndex++) {
                claimsList.add((claimsJsonArray.getString(rulesIndex)));
            }
        }
        claims = claimsList;
        this.parameters = new DTOAttributionInputParameters(obj.getJSONObject("parameters"));

        if (obj.has("referrer") && obj.get("referrer") != JSONObject.NULL) {
            this.referrer = new DTOAttributionInputReferrer(obj.getJSONObject("referrer"), formatter); 
        } else {
            this.referrer = null;
        }
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("appVersion", this.appVersion.toJSON(formatter));
        obj.put("sdkVersion", this.sdkVersion.toJSON(formatter));
        obj.put("user", this.user.toJSON(formatter));
        obj.put("device", this.device.toJSON(formatter));
        JSONArray claimsJsonArray = new JSONArray();
        for (String data : claims) {
            claimsJsonArray.put(data);
        }
        obj.put("claims", claimsJsonArray);
        obj.put("parameters", this.parameters.toJSON(formatter));

        if (referrer != null) {
            obj.put("referrer", this.referrer.toJSON(formatter));
        } else {
            obj.put("referrer", JSONObject.NULL);
        }
        return obj;
    }
}