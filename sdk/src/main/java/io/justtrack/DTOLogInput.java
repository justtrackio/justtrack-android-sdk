package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

class DTOLogInput implements JSONEncodable {
    @NonNull
    private final Iterable<DTOLogMessage> messages;
    @NonNull
    private final Iterable<DTOLogMetric> metrics;
    @NonNull
    private final DTOAppVersion appVersion;
    @NonNull
    private final DTOSdkVersion sdkVersion;
    @NonNull
    private final Date clientDate;

    DTOLogInput(@NonNull DTOLogInput obj) {
        this.messages = obj.messages;
        this.metrics = obj.metrics;
        this.appVersion = obj.appVersion;
        this.sdkVersion = obj.sdkVersion;
        this.clientDate = obj.clientDate;
    }

    DTOLogInput(
            @NonNull Iterable<DTOLogMessage> messages,
            @NonNull Iterable<DTOLogMetric> metrics,
            @NonNull DTOAppVersion appVersion,
            @NonNull DTOSdkVersion sdkVersion,
            @NonNull Date clientDate) {
        this.messages = messages;
        this.metrics = metrics;
        this.appVersion = appVersion;
        this.sdkVersion = sdkVersion;
        this.clientDate = clientDate;
    }

    DTOLogInput(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        ArrayList<DTOLogMessage> messagesList = new ArrayList<>();
        if (obj.has("messages")) {
            JSONArray messagesJsonArray = obj.getJSONArray("messages");
            for (int rulesIndex = 0; rulesIndex < messagesJsonArray.length(); rulesIndex++) {
                messagesList.add(new DTOLogMessage(messagesJsonArray.getJSONObject(rulesIndex), formatter));
            }
        }
        messages = messagesList;
        ArrayList<DTOLogMetric> metricsList = new ArrayList<>();
        if (obj.has("metrics")) {
            JSONArray metricsJsonArray = obj.getJSONArray("metrics");
            for (int rulesIndex = 0; rulesIndex < metricsJsonArray.length(); rulesIndex++) {
                metricsList.add(new DTOLogMetric(metricsJsonArray.getJSONObject(rulesIndex), formatter));
            }
        }
        metrics = metricsList;
        this.appVersion = new DTOAppVersion(obj.getJSONObject("appVersion"));
        this.sdkVersion = new DTOSdkVersion(obj.getJSONObject("sdkVersion"));
        this.clientDate = formatter.parseDate(obj.getString("clientDate"));
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray messagesJsonArray = new JSONArray();
        for (DTOLogMessage data : messages) {
            messagesJsonArray.put(data.toJSON(formatter));
        }
        obj.put("messages", messagesJsonArray);
        JSONArray metricsJsonArray = new JSONArray();
        for (DTOLogMetric data : metrics) {
            metricsJsonArray.put(data.toJSON(formatter));
        }
        obj.put("metrics", metricsJsonArray);
        obj.put("appVersion", this.appVersion.toJSON(formatter));
        obj.put("sdkVersion", this.sdkVersion.toJSON(formatter));
        obj.put("clientDate", formatter.formatDateMilliseconds(this.clientDate));
        return obj;
    }
}