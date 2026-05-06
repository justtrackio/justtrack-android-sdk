package io.justtrack;

import androidx.annotation.NonNull;

/**
 * A CallbackInvoker is our answer to crashes observed with Unity. We implement this once by invoking
 * all callbacks and executing tasks on their own threads. Another implementation moves all callbacks
 * and tasks to the main thread. We need this for the Unity version of our SDK as it can crash if two
 * callbacks to C# happen at the same time from different threads.
 */
interface CallbackInvoker {
    /**
     * Call the provided callback once - not necessary from the current thread.
     *
     * @param callback The callback to call.
     */
    void invoke(@NonNull Runnable callback);

    /**
     * Run the given task either on some thread. If the task can't be called, the rejectedHandler is called.
     *
     * @param task            The callback to call.
     * @param rejectedHandler Called if the task can't be executed.
     */
    void execute(@NonNull Runnable task, @NonNull RejectedExecutionExceptionHandler rejectedHandler);

    /**
     * Wrap a callback such that resolve/reject are called via invoke.
     *
     * @param callback The callback to wrap.
     * @param <V>     The type the callback resolves to.
     * @return Possibly a new callback depending on the implementation of invoke.
     */
    @NonNull
    <V> Callback<V> wrap(@NonNull Callback<V> callback);
}
