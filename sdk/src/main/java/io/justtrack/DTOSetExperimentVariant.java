package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class DTOSetExperimentVariant implements JSONEncodable {
    @NonNull
    private final String installInstanceId;
    @NonNull
    private final String justtrackSdkVersion;
    @NonNull
    private final String appVersionName;
    @NonNull
    private final String appVersionCode;
    @NonNull
    private final String osVersion;
    @NonNull
    private final String experiment;
    @NonNull
    private final String variant;
    @Nullable
    private final List<String> tags;
    @Nullable
    private final Date happenedAt;

    DTOSetExperimentVariant(@NonNull DTOSetExperimentVariant obj) {
        this.installInstanceId = obj.installInstanceId;
        this.justtrackSdkVersion = obj.justtrackSdkVersion;
        this.appVersionName = obj.appVersionName;
        this.appVersionCode = obj.appVersionCode;
        this.osVersion = obj.osVersion;
        this.experiment = obj.experiment;
        this.variant = obj.variant;
        this.tags = obj.tags;
        this.happenedAt = obj.happenedAt;
    }

    DTOSetExperimentVariant(
            @NonNull String installInstanceId,
            @NonNull String justtrackSdkVersion,
            @NonNull String appVersionName,
            @NonNull String appVersionCode,
            @NonNull String osVersion,
            @NonNull String experiment,
            @NonNull String variant,
            @Nullable List<String> tags,
            @Nullable Date happenedAt) {
        this.installInstanceId = installInstanceId;
        this.justtrackSdkVersion = justtrackSdkVersion;
        this.appVersionName = appVersionName;
        this.appVersionCode = appVersionCode;
        this.osVersion = osVersion;
        this.experiment = experiment;
        this.variant = variant;
        this.tags = tags;
        this.happenedAt = happenedAt;
    }

    DTOSetExperimentVariant(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.installInstanceId = obj.getString("installInstanceId");
        this.justtrackSdkVersion = obj.getString("justtrackSdkVersion");
        this.appVersionName = obj.getString("appVersionName");
        this.appVersionCode = obj.getString("appVersionCode");
        this.osVersion = obj.getString("osVersion");
        this.experiment = obj.getString("experiment");
        this.variant = obj.getString("variant");

        if (obj.has("tags") && obj.get("tags") != JSONObject.NULL) {
            ArrayList<String> tagsList = new ArrayList<>();
        if (obj.has("tags")) {
            JSONArray tagsJsonArray = obj.getJSONArray("tags");
            for (int rulesIndex = 0; rulesIndex < tagsJsonArray.length(); rulesIndex++) {
                tagsList.add((tagsJsonArray.getString(rulesIndex)));
            }
        }
        tags = tagsList;
        } else {
            this.tags = null;
        }

        if (obj.has("happenedAt") && obj.get("happenedAt") != JSONObject.NULL) {
            this.happenedAt = formatter.parseDate(obj.getString("happenedAt"));
        } else {
            this.happenedAt = null;
        }
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("installInstanceId", this.installInstanceId);
        obj.put("justtrackSdkVersion", this.justtrackSdkVersion);
        obj.put("appVersionName", this.appVersionName);
        obj.put("appVersionCode", this.appVersionCode);
        obj.put("osVersion", this.osVersion);
        obj.put("experiment", this.experiment);
        obj.put("variant", this.variant);

        if (tags != null) {
            JSONArray tagsJsonArray = new JSONArray();
        for (String data : tags) {
            tagsJsonArray.put(data);
        }
        obj.put("tags", tagsJsonArray);
        } else {
            obj.put("tags", JSONObject.NULL);
        }

        if (happenedAt != null) {
            obj.put("happenedAt", formatter.formatDateMilliseconds(this.happenedAt));
        } else {
            obj.put("happenedAt", JSONObject.NULL);
        }
        return obj;
    }

    @NonNull
    String getInstallInstanceId() {
        return this.installInstanceId;
    }

    @NonNull
    String getJusttrackSdkVersion() {
        return this.justtrackSdkVersion;
    }

    @NonNull
    String getAppVersionName() {
        return this.appVersionName;
    }

    @NonNull
    String getAppVersionCode() {
        return this.appVersionCode;
    }

    @NonNull
    String getOsVersion() {
        return this.osVersion;
    }

    @NonNull
    String getExperiment() {
        return this.experiment;
    }

    @NonNull
    String getVariant() {
        return this.variant;
    }

    @Nullable
    List<String> getTags() {
        return this.tags;
    }

    @Nullable
    Date getHappenedAt() {
        return this.happenedAt;
    }
}