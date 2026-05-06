package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

class DTOSdkVersion implements JSONEncodable {
    @NonNull
    private final int major;
    @NonNull
    private final int minor;
    @NonNull
    private final int patch;
    @NonNull
    private final String name;
    @NonNull
    private final String platform;
    @Nullable
    private final String wrapper;

    DTOSdkVersion(@NonNull DTOSdkVersion obj) {
        this.major = obj.major;
        this.minor = obj.minor;
        this.patch = obj.patch;
        this.name = obj.name;
        this.platform = obj.platform;
        this.wrapper = obj.wrapper;
    }

    DTOSdkVersion(
            @NonNull int major,
            @NonNull int minor,
            @NonNull int patch,
            @NonNull String name,
            @NonNull String platform,
            @Nullable String wrapper) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.name = name;
        this.platform = platform;
        this.wrapper = wrapper;
    }

    DTOSdkVersion(@NonNull JSONObject obj) throws JSONException {
        this.major = obj.getInt("major");
        this.minor = obj.getInt("minor");
        this.patch = obj.getInt("patch");
        this.name = obj.getString("name");
        this.platform = obj.getString("platform");

        if (obj.has("wrapper") && obj.get("wrapper") != JSONObject.NULL) {
            this.wrapper = obj.getString("wrapper");
        } else {
            this.wrapper = null;
        }
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("major", this.major);
        obj.put("minor", this.minor);
        obj.put("patch", this.patch);
        obj.put("name", this.name);
        obj.put("platform", this.platform);

        if (wrapper != null) {
            obj.put("wrapper", this.wrapper);
        } else {
            obj.put("wrapper", JSONObject.NULL);
        }
        return obj;
    }
}