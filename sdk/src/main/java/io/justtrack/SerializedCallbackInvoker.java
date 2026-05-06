package io.justtrack;

import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;

class SerializedCallbackInvoker implements CallbackInvoker {
    @NonNull
    private final Handler handler;

    SerializedCallbackInvoker() {
        HandlerThread handlerThread = new HandlerThread("SerializedCallbackInvokerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void invoke(@NonNull Runnable callback) {
        handler.post(callback);
    }

    @Override
    public void execute(@NonNull Runnable task, @NonNull RejectedExecutionExceptionHandler rejectedHandler) {
        invoke(task);
    }

    @NonNull
    @Override
    public <V> Callback<V> wrap(@NonNull Callback<V> callback) {
        return new Callback<V>() {
            @Override
            public void resolve(V response) {
                invoke(() -> callback.resolve(response));
            }

            @Override
            public void reject(@NonNull Throwable exception) {
                invoke(() -> callback.reject(exception));
            }
        };
    }
}
