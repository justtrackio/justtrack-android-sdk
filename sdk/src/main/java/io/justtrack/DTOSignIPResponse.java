package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOSignIPResponse implements JSONEncodable {
    @NonNull
    private final String ip;
    @NonNull
    private final String type;
    @NonNull
    private final String token;

    DTOSignIPResponse(@NonNull DTOSignIPResponse obj) {
        this.ip = obj.ip;
        this.type = obj.type;
        this.token = obj.token;
    }

    DTOSignIPResponse(
            @NonNull String ip,
            @NonNull String type,
            @NonNull String token) {
        this.ip = ip;
        this.type = type;
        this.token = token;
    }

    DTOSignIPResponse(@NonNull JSONObject obj) throws JSONException {
        this.ip = obj.getString("ip");
        this.type = obj.getString("type");
        this.token = obj.getString("token");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("ip", this.ip);
        obj.put("type", this.type);
        obj.put("token", this.token);
        return obj;
    }

    @NonNull
    String getIp() {
        return this.ip;
    }

    @NonNull
    String getType() {
        return this.type;
    }

    @NonNull
    String getToken() {
        return this.token;
    }
}