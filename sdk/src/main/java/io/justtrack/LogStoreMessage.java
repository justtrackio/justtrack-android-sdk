package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;

class LogStoreMessage extends LogMessageDatum implements LogStoreDatum {
    private final long id;

    LogStoreMessage(@NonNull DTOLogMessage message) {
        super(message);
        this.id = -1;
    }

    LogStoreMessage(long id, @NonNull DTOLogMessage message) {
        super(message);
        this.id = id;
    }

    LogStoreMessage(long id, @NonNull String encoded, @NonNull Formatter formatter) throws JSONException, ParseException {
        super(new JSONObject(encoded), formatter);
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
    }
}
