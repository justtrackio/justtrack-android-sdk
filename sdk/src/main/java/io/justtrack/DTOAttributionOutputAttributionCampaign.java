package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionOutputAttributionCampaign {
    private final int id;
    @NonNull
    private final String name;
    @NonNull
    private final String type;
    private final boolean organic;

    DTOAttributionOutputAttributionCampaign(@NonNull DTOAttributionOutputAttributionCampaign obj) {
        this.id = obj.id;
        this.name = obj.name;
        this.type = obj.type;
        this.organic = obj.organic;
    }

    DTOAttributionOutputAttributionCampaign(
            int id,
            @NonNull String name,
            @NonNull String type,
            boolean organic) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.organic = organic;
    }

    DTOAttributionOutputAttributionCampaign(@NonNull JSONObject obj) throws JSONException {
        this.id = obj.getInt("id");
        this.name = obj.getString("name");
        this.type = obj.getString("type");
        this.organic = obj.getBoolean("organic");
    }

    int getId() {
        return this.id;
    }

    @NonNull
    String getName() {
        return this.name;
    }

    @NonNull
    String getType() {
        return this.type;
    }

    boolean isOrganic() {
        return this.organic;
    }
}