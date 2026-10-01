package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.util.Date;

import io.justtrack.dtos.DTOLogMessage;
import io.justtrack.dtos.LogLevel;

class LogMessageDatum extends DTOLogMessage {
    LogMessageDatum(@NonNull DTOLogMessage obj) {
        super(obj);
    }

    LogMessageDatum(@NonNull LogLevel level, @NonNull String name, @NonNull JSONObject dimensions, @NonNull Date timestamp) {
        super(level, name, dimensions, timestamp);
    }
}
