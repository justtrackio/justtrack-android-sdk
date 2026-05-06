package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DTOAttributionOutputSdkLog implements JSONEncodable {
    @NonNull
    private final List<DTOAttributionOutputSdkRule> rules;
    @NonNull
    private final Map<String, Double> logPercentage;

    DTOAttributionOutputSdkLog(@NonNull DTOAttributionOutputSdkLog obj) {
        this.rules = obj.rules;
        this.logPercentage = obj.logPercentage;
    }

    DTOAttributionOutputSdkLog(
            @NonNull List<DTOAttributionOutputSdkRule> rules,
            @NonNull Map<String, Double> logPercentage) {
        this.rules = rules;
        this.logPercentage = logPercentage;
    }

    DTOAttributionOutputSdkLog(@NonNull JSONObject obj) throws JSONException {
        ArrayList<DTOAttributionOutputSdkRule> rulesList = new ArrayList<>();
        if (obj.has("rules")) {
            JSONArray rulesJsonArray = obj.getJSONArray("rules");
            for (int rulesIndex = 0; rulesIndex < rulesJsonArray.length(); rulesIndex++) {
                rulesList.add(new DTOAttributionOutputSdkRule(rulesJsonArray.getJSONObject(rulesIndex)));
            }
        }
        rules = rulesList;
        logPercentage = new HashMap<>();
        JSONObject logPercentageJsonObject = obj.getJSONObject("logPercentage");
        Iterable<String> logPercentageIterable = logPercentageJsonObject::keys;
        for (String key : logPercentageIterable) {
            Double value = logPercentageJsonObject.getDouble(key);
            logPercentage.put(key, value);
        }
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
        JSONObject logPercentageJsonObject = new JSONObject();
        for (Map.Entry<String, Double> parameter : logPercentage.entrySet()) {
            logPercentageJsonObject.put(parameter.getKey(), parameter.getValue());
        }
        obj.put("logPercentage", logPercentageJsonObject);
        return obj;
    }

    @NonNull
    List<DTOAttributionOutputSdkRule> getRules() {
        return this.rules;
    }

    @NonNull
    Map<String, Double> getLogPercentage() {
        return this.logPercentage;
    }
}