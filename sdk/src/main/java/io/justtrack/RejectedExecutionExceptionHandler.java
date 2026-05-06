package io.justtrack;

import androidx.annotation.NonNull;

import java.util.concurrent.RejectedExecutionException;

interface RejectedExecutionExceptionHandler {
    void handleRejectedExecution(@NonNull RejectedExecutionException exception);
}
