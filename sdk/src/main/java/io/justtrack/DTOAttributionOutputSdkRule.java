package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionOutputSdkRule implements JSONEncodable {
    @NonNull
    private final String rule;
    private final boolean drop;
    @NonNull
    private final JSONObject dimensions;

    DTOAttributionOutputSdkRule(@NonNull DTOAttributionOutputSdkRule obj) {
        this.rule = obj.rule;
        this.drop = obj.drop;
        this.dimensions = obj.dimensions;
    }

    DTOAttributionOutputSdkRule(
            @NonNull String rule,
            boolean drop,
            @NonNull JSONObject dimensions) {
        this.rule = rule;
        this.drop = drop;
        this.dimensions = dimensions;
    }

    DTOAttributionOutputSdkRule(@NonNull JSONObject obj) throws JSONException {
        this.rule = obj.getString("rule");
        this.drop = obj.getBoolean("drop");
        this.dimensions = obj.getJSONObject("dimensions");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("rule", this.rule);
        obj.put("drop", this.drop);
        obj.put("dimensions", this.dimensions);
        return obj;
    }

    @NonNull
    String getRule() {
        return this.rule;
    }

    boolean isDrop() {
        return this.drop;
    }

    @NonNull
    JSONObject getDimensions() {
        return this.dimensions;
    }
}