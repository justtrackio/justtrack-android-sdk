package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

class DTOAppEventUser implements JSONEncodable {
    @Nullable
    private final String deviceId;
    @Nullable
    private final String countryIso;
    @Nullable
    private final String localeCode;
    @NonNull
    private final UUID userId;
    @NonNull
    private final UUID installInstanceId;

    DTOAppEventUser(@NonNull DTOAppEventUser obj) {
        this.deviceId = obj.deviceId;
        this.countryIso = obj.countryIso;
        this.localeCode = obj.localeCode;
        this.userId = obj.userId;
        this.installInstanceId = obj.installInstanceId;
    }

    DTOAppEventUser(
            @Nullable String deviceId,
            @Nullable String countryIso,
            @Nullable String localeCode,
            @NonNull UUID userId,
            @NonNull UUID installInstanceId) {
        this.deviceId = deviceId;
        this.countryIso = countryIso;
        this.localeCode = localeCode;
        this.userId = userId;
        this.installInstanceId = installInstanceId;
    }

    DTOAppEventUser(@NonNull JSONObject obj) throws JSONException {

        if (obj.has("deviceId") && obj.get("deviceId") != JSONObject.NULL) {
            this.deviceId = obj.getString("deviceId");
        } else {
            this.deviceId = null;
        }

        if (obj.has("countryIso") && obj.get("countryIso") != JSONObject.NULL) {
            this.countryIso = obj.getString("countryIso");
        } else {
            this.countryIso = null;
        }

        if (obj.has("localeCode") && obj.get("localeCode") != JSONObject.NULL) {
            this.localeCode = obj.getString("localeCode");
        } else {
            this.localeCode = null;
        }
        this.userId = UUID.fromString(obj.getString("userId"));
        this.installInstanceId = UUID.fromString(obj.getString("installInstanceId"));
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();

        if (deviceId != null) {
            obj.put("deviceId", this.deviceId);
        } else {
            obj.put("deviceId", JSONObject.NULL);
        }

        if (countryIso != null) {
            obj.put("countryIso", this.countryIso);
        } else {
            obj.put("countryIso", JSONObject.NULL);
        }

        if (localeCode != null) {
            obj.put("localeCode", this.localeCode);
        } else {
            obj.put("localeCode", JSONObject.NULL);
        }
        obj.put("userId", this.userId.toString());
        obj.put("installInstanceId", this.installInstanceId.toString());
        return obj;
    }
}