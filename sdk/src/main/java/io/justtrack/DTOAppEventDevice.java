package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;

class DTOAppEventDevice implements JSONEncodable {
    @NonNull
    private final ConnectionType connectionType;
    @NonNull
    private final DTOAppEventDeviceOS os;
    @NonNull
    private final Date date;

    DTOAppEventDevice(@NonNull DTOAppEventDevice obj) {
        this.connectionType = obj.connectionType;
        this.os = obj.os;
        this.date = obj.date;
    }

    DTOAppEventDevice(
            @NonNull ConnectionType connectionType,
            @NonNull DTOAppEventDeviceOS os,
            @NonNull Date date) {
        this.connectionType = connectionType;
        this.os = os;
        this.date = date;
    }

    DTOAppEventDevice(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.connectionType = ConnectionType.valueOf(obj.getString("connectionType").toUpperCase());
        this.os = new DTOAppEventDeviceOS(obj.getJSONObject("os")); 
        this.date = formatter.parseDate(obj.getString("date"));
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("connectionType", this.connectionType.toString());
        obj.put("os", this.os.toJSON(formatter));
        obj.put("date", formatter.formatDateMilliseconds(this.date));
        return obj;
    }
}