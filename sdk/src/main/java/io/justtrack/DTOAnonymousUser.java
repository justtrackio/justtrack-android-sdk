package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAnonymousUser implements JSONEncodable {
    @NonNull
    private final String installInstanceId;
    @Nullable
    private final String deviceId;
    @Nullable
    private final String androidId;

    DTOAnonymousUser(@NonNull DTOAnonymousUser obj) {
        this.installInstanceId = obj.installInstanceId;
        this.deviceId = obj.deviceId;
        this.androidId = obj.androidId;
    }

    DTOAnonymousUser(
            @NonNull String installInstanceId,
            @Nullable String deviceId,
            @Nullable String androidId) {
        this.installInstanceId = installInstanceId;
        this.deviceId = deviceId;
        this.androidId = androidId;
    }

    DTOAnonymousUser(@NonNull JSONObject obj) throws JSONException {
        this.installInstanceId = obj.getString("installInstanceId");

        if (obj.has("deviceId") && obj.get("deviceId") != JSONObject.NULL) {
            this.deviceId = obj.getString("deviceId");
        } else {
            this.deviceId = null;
        }

        if (obj.has("androidId") && obj.get("androidId") != JSONObject.NULL) {
            this.androidId = obj.getString("androidId");
        } else {
            this.androidId = null;
        }
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("installInstanceId", this.installInstanceId);

        if (deviceId != null) {
            obj.put("deviceId", this.deviceId);
        } else {
            obj.put("deviceId", JSONObject.NULL);
        }

        if (androidId != null) {
            obj.put("androidId", this.androidId);
        } else {
            obj.put("androidId", JSONObject.NULL);
        }
        return obj;
    }

    @NonNull
    String getInstallInstanceId() {
        return this.installInstanceId;
    }

    @Nullable
    String getDeviceId() {
        return this.deviceId;
    }

    @Nullable
    String getAndroidId() {
        return this.androidId;
    }
}