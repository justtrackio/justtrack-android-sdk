package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;

class DTOAttributionInputReferrer implements JSONEncodable {
    @NonNull
    private final String value;
    @NonNull
    private final Date clickDate;
    @NonNull
    private final Date installBeginDate;
    @NonNull
    private final Date clientDate;
    @NonNull
    private final Date serverClickDate;
    @NonNull
    private final Date serverInstallBeginDate;
    @Nullable
    private final String installVersion;

    DTOAttributionInputReferrer(@NonNull DTOAttributionInputReferrer obj) {
        this.value = obj.value;
        this.clickDate = obj.clickDate;
        this.installBeginDate = obj.installBeginDate;
        this.clientDate = obj.clientDate;
        this.serverClickDate = obj.serverClickDate;
        this.serverInstallBeginDate = obj.serverInstallBeginDate;
        this.installVersion = obj.installVersion;
    }

    DTOAttributionInputReferrer(
            @NonNull String value,
            @NonNull Date clickDate,
            @NonNull Date installBeginDate,
            @NonNull Date clientDate,
            @NonNull Date serverClickDate,
            @NonNull Date serverInstallBeginDate,
            @Nullable String installVersion) {
        this.value = value;
        this.clickDate = clickDate;
        this.installBeginDate = installBeginDate;
        this.clientDate = clientDate;
        this.serverClickDate = serverClickDate;
        this.serverInstallBeginDate = serverInstallBeginDate;
        this.installVersion = installVersion;
    }

    DTOAttributionInputReferrer(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.value = obj.getString("value");
        this.clickDate = formatter.parseDate(obj.getString("clickDate"));
        this.installBeginDate = formatter.parseDate(obj.getString("installBeginDate"));
        this.clientDate = formatter.parseDate(obj.getString("clientDate"));
        this.serverClickDate = formatter.parseDate(obj.getString("serverClickDate"));
        this.serverInstallBeginDate = formatter.parseDate(obj.getString("serverInstallBeginDate"));

        if (obj.has("installVersion") && obj.get("installVersion") != JSONObject.NULL) {
            this.installVersion = obj.getString("installVersion");
        } else {
            this.installVersion = null;
        }
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("value", this.value);
        obj.put("clickDate", formatter.formatDateMilliseconds(this.clickDate));
        obj.put("installBeginDate", formatter.formatDateMilliseconds(this.installBeginDate));
        obj.put("clientDate", formatter.formatDateMilliseconds(this.clientDate));
        obj.put("serverClickDate", formatter.formatDateMilliseconds(this.serverClickDate));
        obj.put("serverInstallBeginDate", formatter.formatDateMilliseconds(this.serverInstallBeginDate));

        if (installVersion != null) {
            obj.put("installVersion", this.installVersion);
        } else {
            obj.put("installVersion", JSONObject.NULL);
        }
        return obj;
    }
}