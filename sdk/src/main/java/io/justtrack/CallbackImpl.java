package io.justtrack;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

class CallbackImpl<T, E> implements Callback<T> {
    @NonNull
    private final Callback<T> callback;
    @NonNull
    private final AtomicBoolean fulfilled;

    CallbackImpl(@NonNull Callback<T> callback) {
        this.callback = callback;
        this.fulfilled = new AtomicBoolean(false);
    }

    @Override
    public void resolve(@NonNull T response) {
        if (fulfilled.compareAndSet(false, true)) {
            callback.resolve(response);
        }
    }

    @Override
    public void reject(@NonNull Throwable exception) {
        if (fulfilled.compareAndSet(false, true)) {
            callback.reject(exception);
        }
    }
}
