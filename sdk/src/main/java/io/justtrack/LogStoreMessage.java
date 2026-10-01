package io.justtrack;

import androidx.annotation.NonNull;

import io.justtrack.dtos.DTOLogMessage;

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

    @Override
    public long getId() {
        return id;
    }
}
