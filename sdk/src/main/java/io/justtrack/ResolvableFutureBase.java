package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract class ResolvableFutureBase<V> implements AsyncFuture<V>, Callback<V> {
    private static final int PENDING = 0;
    private static final int RESOLVED = 1;
    private static final int REJECTED = 2;

    @Nullable
    private V result;
    @Nullable
    private ExecutionException error;
    private int state;
    @NonNull
    private final List<Callback<V>> waitingList;

    ResolvableFutureBase() {
        result = null;
        error = null;
        state = PENDING;
        waitingList = new ArrayList<>();
    }

    @Override
    public void resolve(V response) {
        List<Callback<V>> waitingListCopy = null;

        // Deadlock-Safety: This method only updates the state and doesn't take any additional locks
        // (and notifyAll requires holding the lock)
        synchronized (this) {
            if (state == PENDING) {
                state = RESOLVED;
                result = response;
                waitingListCopy = new ArrayList<>(waitingList);
                waitingList.clear();
                notifyAll();
            }
        }

        if (waitingListCopy != null) {
            for (Callback<V> waiting : waitingListCopy) {
                waiting.resolve(response);
            }
        }
    }

    @Override
    public void reject(@NonNull Throwable exception) {
        List<Callback<V>> waitingListCopy = null;
        ExecutionException wrappedError = new ExecutionException(exception);

        // Deadlock-Safety: This method only updates the state and doesn't take any additional locks
        // (and notifyAll requires holding the lock)
        synchronized (this) {
            if (state == PENDING) {
                state = REJECTED;
                error = wrappedError;
                waitingListCopy = new ArrayList<>(waitingList);
                waitingList.clear();
                notifyAll();
            }
        }

        if (waitingListCopy != null) {
            for (Callback<V> waiting : waitingListCopy) {
                waiting.reject(wrappedError);
            }
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // you can't cancel this type of future because we just get notified about the result
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    // Deadlock-Safety: This method only reads the state
    public synchronized boolean isDone() {
        return hasResult();
    }

    @Override
    // Deadlock-Safety: This method only reads the state or creates new exceptions (which don't take locks).
    // (and wait requires holding the lock and releases it for the duration of the call)
    public synchronized V get() throws ExecutionException, InterruptedException {
        while (true) {
            if (hasResult()) {
                return getResult();
            }
            wait();
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        long maxWaitTime = unit.toMillis(timeout);

        // Deadlock-Safety: Only reads the state, then releases the lock in `wait`, then reads the state again
        synchronized (this) {
            if (hasResult()) {
                return getResult();
            }
            wait(maxWaitTime);

            if (hasResult()) {
                return getResult();
            }

            throw new TimeoutException();
        }
    }

    // Thread-Safety: requires a lock on `this` to be held.
    private boolean hasResult() {
        return state == RESOLVED || state == REJECTED;
    }

    @Nullable
    // Thread-Safety: requires a lock on `this` to be held.
    private V getResult() throws ExecutionException {
        switch (state) {
            case RESOLVED:
                return result;
            case REJECTED:
                throw error != null ? error : new ExecutionException(new RuntimeException("no error was set"));
            default:
                return null;
        }
    }

    @Override
    public void registerCallback(@NonNull Callback<V> callback) {
        // Deadlock-Safety: Only reads the state and adds the callback to the waiting list if needed.
        synchronized (this) {
            switch (state) {
                case RESOLVED:
                    callback.resolve(result);
                    return;
                case REJECTED:
                    callback.reject(error != null ? error : new ExecutionException(new RuntimeException("no error was set")));
                    return;
                default:
                    break;
            }

            waitingList.add(callback);
        }
    }
}
