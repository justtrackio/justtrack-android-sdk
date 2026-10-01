package io.justtrack;

import androidx.annotation.NonNull;

import io.justtrack.dtos.DTOLogMetric;

class LogStoreMetric extends DTOLogMetric implements LogStoreDatum {
    private final long id;

    LogStoreMetric(@NonNull DTOLogMetric metric) {
        super(metric);
        this.id = -1;
    }

    LogStoreMetric(long id, @NonNull DTOLogMetric metric) {
        super(metric);
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
    }
}
