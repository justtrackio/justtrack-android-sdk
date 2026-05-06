package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

class DTOAppEvent implements JSONEncodable {
    @NonNull
    private final DTOAppVersion appVersion;
    @NonNull
    private final DTOSdkVersion sdkVersion;
    @NonNull
    private final DTOAppEventUser user;
    @NonNull
    private final DTOAppEventDevice device;
    @NonNull
    private final List<DTOAppEventEvent> events;

    DTOAppEvent(
            @NonNull DTOAppVersion appVersion,
            @NonNull DTOSdkVersion sdkVersion,
            @NonNull DTOAppEventUser user,
            @NonNull DTOAppEventDevice device,
            @NonNull List<DTOAppEventEvent> events) {
        this.appVersion = appVersion;
        this.sdkVersion = sdkVersion;
        this.user = user;
        this.device = device;
        this.events = events;
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("appVersion", this.appVersion.toJSON(formatter));
        obj.put("sdkVersion", this.sdkVersion.toJSON(formatter));
        obj.put("user", this.user.toJSON(formatter));
        obj.put("device", this.device.toJSON(formatter));
        JSONArray eventsJsonArray = new JSONArray();
        for (DTOAppEventEvent data : events) {
            eventsJsonArray.put(data.toJSON(formatter));
        }
        obj.put("events", eventsJsonArray);
        return obj;
    }

    @NonNull
    DTOAppVersion getAppVersion() {
        return this.appVersion;
    }

    @NonNull
    DTOSdkVersion getSdkVersion() {
        return this.sdkVersion;
    }

    @NonNull
    DTOAppEventUser getUser() {
        return this.user;
    }

    @NonNull
    DTOAppEventDevice getDevice() {
        return this.device;
    }

    @NonNull
    List<DTOAppEventEvent> getEvents() {
        return this.events;
    }
}