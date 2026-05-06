package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

enum LogLevel {
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error");

    @NonNull
    private final String level;

    LogLevel(@NonNull String level) {
        this.level = level;
    }

    @NonNull
    @Override
    public String toString() {
        return level;
    }

    @Nullable
    static LogLevel fromString(@Nullable String string) {
        if (string == null) {
            return null;
        }

        for (LogLevel logLevel : values()) {
            if (logLevel.toString().equals(string)) {
                return logLevel;
            }
        }
        return null;
    }
}
