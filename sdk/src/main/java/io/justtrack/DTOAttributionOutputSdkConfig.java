package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionOutputSdkConfig implements JSONEncodable {
    @NonNull
    private final DTOAttributionOutputSdkLog log;
    @NonNull
    private final DTOAttributionOutputSdkMetric metric;
    @NonNull
    private final DTOAttributionOutputSdkEvent event;

    DTOAttributionOutputSdkConfig(@NonNull DTOAttributionOutputSdkConfig obj) {
        this.log = obj.log;
        this.metric = obj.metric;
        this.event = obj.event;
    }

    DTOAttributionOutputSdkConfig(
            @NonNull DTOAttributionOutputSdkLog log,
            @NonNull DTOAttributionOutputSdkMetric metric,
            @NonNull DTOAttributionOutputSdkEvent event) {
        this.log = log;
        this.metric = metric;
        this.event = event;
    }

    DTOAttributionOutputSdkConfig(@NonNull JSONObject obj) throws JSONException {
        this.log = new DTOAttributionOutputSdkLog(obj.getJSONObject("log"));
        this.metric = new DTOAttributionOutputSdkMetric(obj.getJSONObject("metric"));
        this.event = new DTOAttributionOutputSdkEvent(obj.getJSONObject("event")); 
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("log", this.log.toJSON(formatter));
        obj.put("metric", this.metric.toJSON(formatter));
        obj.put("event", this.event.toJSON(formatter));
        return obj;
    }

    @NonNull
    DTOAttributionOutputSdkLog getLog() {
        return this.log;
    }

    @NonNull
    DTOAttributionOutputSdkMetric getMetric() {
        return this.metric;
    }

    @NonNull
    DTOAttributionOutputSdkEvent getEvent() {
        return this.event;
    }
}