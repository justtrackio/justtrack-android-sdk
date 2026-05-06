package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;

class DTOLogMessage implements JSONEncodable {
    @NonNull
    private final LogLevel level;
    @NonNull
    private final String message;
    @NonNull
    private final JSONObject fields;
    @NonNull
    private final Date timestamp;

    DTOLogMessage(@NonNull DTOLogMessage obj) {
        this.level = obj.level;
        this.message = obj.message;
        this.fields = obj.fields;
        this.timestamp = obj.timestamp;
    }

    DTOLogMessage(
            @NonNull LogLevel level,
            @NonNull String message,
            @NonNull JSONObject fields,
            @NonNull Date timestamp) {
        this.level = level;
        this.message = message;
        this.fields = fields;
        this.timestamp = timestamp;
    }

    DTOLogMessage(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.level = LogLevel.valueOf(obj.getString("level").toUpperCase());
        this.message = obj.getString("message");
        this.fields = obj.getJSONObject("fields");
        this.timestamp = formatter.parseDate(obj.getString("timestamp"));
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("level", this.level.toString());
        obj.put("message", this.message);
        obj.put("fields", this.fields);
        obj.put("timestamp", formatter.formatDateMilliseconds(this.timestamp));
        return obj;
    }

    @NonNull
    LogLevel getLevel() {
        return this.level;
    }

    @NonNull
    String getMessage() {
        return this.message;
    }

    @NonNull
    JSONObject getFields() {
        return this.fields;
    }

    @NonNull
    protected Date getTimestamp() {
        return this.timestamp;
    }
}