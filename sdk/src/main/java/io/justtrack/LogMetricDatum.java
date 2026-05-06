package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONObject;

class LogMetricDatum extends DTOLogMetric implements NamedDatum {
    LogMetricDatum(@NonNull DTOLogMetric obj) {
        super(obj);
    }

    @NonNull
    @Override
    public String getDatum() {
        return getMetric();
    }

    @NonNull
    @Override
    public JSONObject getDimensions() {
        return super.getDimensions();
    }
}
