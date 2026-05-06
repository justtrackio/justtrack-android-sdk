package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.justtrack.log.Logger;
import io.justtrack.log.LoggerFields;
import io.justtrack.log.LoggerFieldsBuilder;

public class TestLoggerImpl extends LoggerImpl implements HttpLogger {
    @NonNull
    private final List<String> errors = new ArrayList<>();

    @Override
    public void error(@NonNull String message, LoggerFields... fields) {
        super.error(message, fields);
        errors.add(encodeMessage(message, fields));
    }

    @Override
    public void error(@NonNull String message, @NonNull Throwable exception, LoggerFields... fields) {
        super.error(message, exception, fields);
        errors.add(encodeMessage(encodeMessage(message, fields), new LoggerFieldsBuilder().with("exception", exception)));
    }

    @Override
    public void setAdvertiserId(@NonNull String advertiserId) {
    }

    @Override
    public void setUser(@NonNull AsyncFuture<UUID> userId, @NonNull AsyncFuture<String> installId) {
    }

    @Override
    public void setUser(@Nullable UUID userId, @NonNull String installId) {
    }

    @NonNull
    @Override
    public Logger getFallback() {
        return this;
    }

    @Override
    public void sendToServer() {
        // nop
    }

    @Override
    public void setBreadCrumbReporter(BreadCrumbReporter reporter) {
        //nop
    }

    @Override
    public void setLogAndMetricRules(@NonNull DTOAttributionOutputSdkConfig config) {
        // nop
    }

    @Override
    public void close() {
        // nop
    }

    public void assertHasError(boolean expectedError) {
        if (expectedError) {
            Assert.assertFalse("There should be an error logged", errors.isEmpty());
        } else {
            Assert.assertArrayEquals("The list of errors should be empty", new String[0], errors.toArray(new String[0]));
        }

        errors.clear();
    }
}
