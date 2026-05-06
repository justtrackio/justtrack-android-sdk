package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAppVersion implements JSONEncodable {
    @NonNull
    private final String name;
    @NonNull
    private final String code;

    DTOAppVersion(@NonNull DTOAppVersion obj) {
        this.name = obj.name;
        this.code = obj.code;
    }

    DTOAppVersion(
            @NonNull String name,
            @NonNull String code) {
        this.name = name;
        this.code = code;
    }

    DTOAppVersion(@NonNull JSONObject obj) throws JSONException {
        this.name = obj.getString("name");
        this.code = obj.getString("code");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("name", this.name);
        obj.put("code", this.code);
        return obj;
    }
}