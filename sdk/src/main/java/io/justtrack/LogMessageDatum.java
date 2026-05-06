package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;

class LogMessageDatum extends DTOLogMessage implements NamedDatum {
    LogMessageDatum(@NonNull DTOLogMessage obj) {
        super(obj);
    }

    LogMessageDatum(@NonNull LogLevel level, @NonNull String name, @NonNull JSONObject dimensions, @NonNull Date timestamp) {
        super(level, name, dimensions, timestamp);
    }

    LogMessageDatum(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        super(obj, formatter);
    }

    @NonNull
    @Override
    public String getDatum() {
        return getMessage();
    }

    @NonNull
    @Override
    public JSONObject getDimensions() {
        return getFields();
    }
}
