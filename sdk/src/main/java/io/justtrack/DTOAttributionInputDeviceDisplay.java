package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionInputDeviceDisplay implements JSONEncodable {
    private final int width;
    private final int height;

    DTOAttributionInputDeviceDisplay(@NonNull DTOAttributionInputDeviceDisplay obj) {
        this.width = obj.width;
        this.height = obj.height;
    }

    DTOAttributionInputDeviceDisplay(
            int width,
            int height) {
        this.width = width;
        this.height = height;
    }

    DTOAttributionInputDeviceDisplay(@NonNull JSONObject obj) throws JSONException {
        this.width = obj.getInt("width");
        this.height = obj.getInt("height");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("width", this.width);
        obj.put("height", this.height);
        return obj;
    }
}