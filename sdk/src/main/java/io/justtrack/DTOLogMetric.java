package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;

class DTOLogMetric implements JSONEncodable {
    @NonNull
    private final String metric;
    @NonNull
    private final JSONObject dimensions;
    private final double value;
    @NonNull
    private final String unit;
    @NonNull
    private final Date timestamp;

    DTOLogMetric(@NonNull DTOLogMetric obj) {
        this.metric = obj.metric;
        this.dimensions = obj.dimensions;
        this.value = obj.value;
        this.unit = obj.unit;
        this.timestamp = obj.timestamp;
    }

    DTOLogMetric(
            @NonNull String metric,
            @NonNull JSONObject dimensions,
            double value,
            @NonNull String unit,
            @NonNull Date timestamp) {
        this.metric = metric;
        this.dimensions = dimensions;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    DTOLogMetric(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.metric = obj.getString("metric");
        this.dimensions = obj.getJSONObject("dimensions");
        this.value = obj.getDouble("value");
        this.unit = obj.getString("unit");
        this.timestamp = formatter.parseDate(obj.getString("timestamp"));
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("metric", this.metric);
        obj.put("dimensions", this.dimensions);
        obj.put("value", this.value);
        obj.put("unit", this.unit);
        obj.put("timestamp", formatter.formatDateMilliseconds(this.timestamp));
        return obj;
    }

    @NonNull
    String getMetric() {
        return this.metric;
    }

    @NonNull
    JSONObject getDimensions() {
        return this.dimensions;
    }

    protected double getValue() {
        return this.value;
    }

    @NonNull
    protected String getUnit() {
        return this.unit;
    }

    @NonNull
    protected Date getTimestamp() {
        return this.timestamp;
    }
}