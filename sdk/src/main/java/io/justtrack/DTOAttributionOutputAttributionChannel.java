package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionOutputAttributionChannel {
    private final int id;
    @NonNull
    private final String name;
    private final boolean incent;

    DTOAttributionOutputAttributionChannel(@NonNull DTOAttributionOutputAttributionChannel obj) {
        this.id = obj.id;
        this.name = obj.name;
        this.incent = obj.incent;
    }

    DTOAttributionOutputAttributionChannel(
            int id,
            @NonNull String name,
            boolean incent) {
        this.id = id;
        this.name = name;
        this.incent = incent;
    }

    DTOAttributionOutputAttributionChannel(@NonNull JSONObject obj) throws JSONException {
        this.id = obj.getInt("id");
        this.name = obj.getString("name");
        this.incent = obj.getBoolean("incent");
    }

    int getId() {
        return this.id;
    }

    @NonNull
    String getName() {
        return this.name;
    }

    boolean isIncent() {
        return this.incent;
    }
}