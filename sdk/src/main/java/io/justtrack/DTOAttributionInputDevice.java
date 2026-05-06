package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionInputDevice implements JSONEncodable {
    @NonNull
    private final String name;
    @NonNull
    private final String model;
    @NonNull
    private final String product;
    @NonNull
    private final DeviceType type;
    @NonNull
    private final DTOAttributionInputDeviceOS os;
    @NonNull
    private final DTOAttributionInputDeviceDisplay display;

    DTOAttributionInputDevice(@NonNull DTOAttributionInputDevice obj) {
        this.name = obj.name;
        this.model = obj.model;
        this.product = obj.product;
        this.type = obj.type;
        this.os = obj.os;
        this.display = obj.display;
    }

    DTOAttributionInputDevice(
            @NonNull String name,
            @NonNull String model,
            @NonNull String product,
            @NonNull DeviceType type,
            @NonNull DTOAttributionInputDeviceOS os,
            @NonNull DTOAttributionInputDeviceDisplay display) {
        this.name = name;
        this.model = model;
        this.product = product;
        this.type = type;
        this.os = os;
        this.display = display;
    }

    DTOAttributionInputDevice(@NonNull JSONObject obj) throws JSONException {
        this.name = obj.getString("name");
        this.model = obj.getString("model");
        this.product = obj.getString("product");
        this.type = DeviceType.valueOf(obj.getString("type").toUpperCase());
        this.os = new DTOAttributionInputDeviceOS(obj.getJSONObject("os")); 
        this.display = new DTOAttributionInputDeviceDisplay(obj.getJSONObject("display")); 
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("name", this.name);
        obj.put("model", this.model);
        obj.put("product", this.product);
        obj.put("type", this.type.toString());
        obj.put("os", this.os.toJSON(formatter));
        obj.put("display", this.display.toJSON(formatter));
        return obj;
    }
}