package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionInputUser implements JSONEncodable {
    @Nullable
    private final String userId;
    @Nullable
    private final String customUserId;
    @NonNull
    private final String installInstanceId;
    @NonNull
    private final String deviceId;
    @Nullable
    private final String advertiserId;
    @Nullable
    private final String trackingId;
    @NonNull
    private final String trackingProvider;
    @Nullable
    private final String countryIso;
    @Nullable
    private final String appSetId;
    private final boolean hasLimitedAdTracking;

    DTOAttributionInputUser(@NonNull DTOAttributionInputUser obj) {
        this.userId = obj.userId;
        this.customUserId = obj.customUserId;
        this.installInstanceId = obj.installInstanceId;
        this.deviceId = obj.deviceId;
        this.advertiserId = obj.advertiserId;
        this.trackingId = obj.trackingId;
        this.trackingProvider = obj.trackingProvider;
        this.countryIso = obj.countryIso;
        this.appSetId = obj.appSetId;
        this.hasLimitedAdTracking = obj.hasLimitedAdTracking;
    }

    DTOAttributionInputUser(
            @Nullable String userId,
            @Nullable String customUserId,
            @NonNull String installInstanceId,
            @NonNull String deviceId,
            @Nullable String advertiserId,
            @Nullable String trackingId,
            @NonNull String trackingProvider,
            @Nullable String countryIso,
            @Nullable String appSetId,
            boolean hasLimitedAdTracking) {
        this.userId = userId;
        this.customUserId = customUserId;
        this.installInstanceId = installInstanceId;
        this.deviceId = deviceId;
        this.advertiserId = advertiserId;
        this.trackingId = trackingId;
        this.trackingProvider = trackingProvider;
        this.countryIso = countryIso;
        this.appSetId = appSetId;
        this.hasLimitedAdTracking = hasLimitedAdTracking;
    }

    DTOAttributionInputUser(@NonNull JSONObject obj) throws JSONException {

        if (obj.has("userId") && obj.get("userId") != JSONObject.NULL) {
            this.userId = obj.getString("userId");
        } else {
            this.userId = null;
        }

        if (obj.has("customUserId") && obj.get("customUserId") != JSONObject.NULL) {
            this.customUserId = obj.getString("customUserId");
        } else {
            this.customUserId = null;
        }
        this.installInstanceId = obj.getString("installInstanceId");
        this.deviceId = obj.getString("deviceId");

        if (obj.has("advertiserId") && obj.get("advertiserId") != JSONObject.NULL) {
            this.advertiserId = obj.getString("advertiserId");
        } else {
            this.advertiserId = null;
        }

        if (obj.has("trackingId") && obj.get("trackingId") != JSONObject.NULL) {
            this.trackingId = obj.getString("trackingId");
        } else {
            this.trackingId = null;
        }
        this.trackingProvider = obj.getString("trackingProvider");

        if (obj.has("countryIso") && obj.get("countryIso") != JSONObject.NULL) {
            this.countryIso = obj.getString("countryIso");
        } else {
            this.countryIso = null;
        }

        if (obj.has("appSetId") && obj.get("appSetId") != JSONObject.NULL) {
            this.appSetId = obj.getString("appSetId");
        } else {
            this.appSetId = null;
        }
        this.hasLimitedAdTracking = obj.getBoolean("hasLimitedAdTracking");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();

        if (userId != null) {
            obj.put("userId", this.userId);
        } else {
            obj.put("userId", JSONObject.NULL);
        }

        if (customUserId != null) {
            obj.put("customUserId", this.customUserId);
        } else {
            obj.put("customUserId", JSONObject.NULL);
        }
        obj.put("installInstanceId", this.installInstanceId);
        obj.put("deviceId", this.deviceId);

        if (advertiserId != null) {
            obj.put("advertiserId", this.advertiserId);
        } else {
            obj.put("advertiserId", JSONObject.NULL);
        }

        if (trackingId != null) {
            obj.put("trackingId", this.trackingId);
        } else {
            obj.put("trackingId", JSONObject.NULL);
        }
        obj.put("trackingProvider", this.trackingProvider);

        if (countryIso != null) {
            obj.put("countryIso", this.countryIso);
        } else {
            obj.put("countryIso", JSONObject.NULL);
        }

        if (appSetId != null) {
            obj.put("appSetId", this.appSetId);
        } else {
            obj.put("appSetId", JSONObject.NULL);
        }
        obj.put("hasLimitedAdTracking", this.hasLimitedAdTracking);
        return obj;
    }
}