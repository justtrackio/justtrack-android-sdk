package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionInputDeviceOS implements JSONEncodable {
    @NonNull
    private final String version;
    @NonNull
    private final String name;

    DTOAttributionInputDeviceOS(@NonNull DTOAttributionInputDeviceOS obj) {
        this.version = obj.version;
        this.name = obj.name;
    }

    DTOAttributionInputDeviceOS(
            @NonNull String version,
            @NonNull String name) {
        this.version = version;
        this.name = name;
    }

    DTOAttributionInputDeviceOS(@NonNull JSONObject obj) throws JSONException {
        this.version = obj.getString("version");
        this.name = obj.getString("name");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("version", this.version);
        obj.put("name", this.name);
        return obj;
    }
}