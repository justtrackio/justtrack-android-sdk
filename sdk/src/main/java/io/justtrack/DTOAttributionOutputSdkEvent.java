package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class DTOAttributionOutputSdkEvent implements JSONEncodable {
    @NonNull
    private final List<DTOAttributionOutputSdkRule> rules;

    DTOAttributionOutputSdkEvent(@NonNull DTOAttributionOutputSdkEvent obj) {
        this.rules = obj.rules;
    }

    DTOAttributionOutputSdkEvent(
            @NonNull List<DTOAttributionOutputSdkRule> rules) {
        this.rules = rules;
    }

    DTOAttributionOutputSdkEvent(@NonNull JSONObject obj) throws JSONException {
        ArrayList<DTOAttributionOutputSdkRule> rulesList = new ArrayList<>();
        if (obj.has("rules")) {
            JSONArray rulesJsonArray = obj.getJSONArray("rules");
            for (int rulesIndex = 0; rulesIndex < rulesJsonArray.length(); rulesIndex++) {
                rulesList.add(new DTOAttributionOutputSdkRule(rulesJsonArray.getJSONObject(rulesIndex)));
            }
        }
        rules = rulesList;
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray rulesJsonArray = new JSONArray();
        for (DTOAttributionOutputSdkRule data : rules) {
            rulesJsonArray.put(data.toJSON(formatter));
        }
        obj.put("rules", rulesJsonArray);
        return obj;
    }

    @NonNull
    List<DTOAttributionOutputSdkRule> getRules() {
        return this.rules;
    }
}