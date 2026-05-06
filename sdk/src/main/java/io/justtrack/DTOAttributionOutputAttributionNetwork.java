package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionOutputAttributionNetwork {
    private final int id;
    @NonNull
    private final String name;

    DTOAttributionOutputAttributionNetwork(@NonNull DTOAttributionOutputAttributionNetwork obj) {
        this.id = obj.id;
        this.name = obj.name;
    }

    DTOAttributionOutputAttributionNetwork(
            int id,
            @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    DTOAttributionOutputAttributionNetwork(@NonNull JSONObject obj) throws JSONException {
        this.id = obj.getInt("id");
        this.name = obj.getString("name");
    }

    int getId() {
        return this.id;
    }

    @NonNull
    String getName() {
        return this.name;
    }
}