package io.justtrack;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

class PeriodicLogsPublisher {
    private static final long INITIAL_MILLIS = 5_000;
    private static final long PAUSE_MILLIS = 1_000;
    private static final long PERIOD_MILLIS = 60_000;

    private static final int STATE_RUNNING = 0;
    private static final int STATE_STOPPING = 1;
    private static final int STATE_STOPPED = 2;

    private final @NonNull HttpLogger httpLogger;
    private final @NonNull Handler handler;
    private final @NonNull AtomicInteger state;
    private final @NonNull AtomicInteger nextScheduled;

    PeriodicLogsPublisher(@NonNull HttpLogger httpLogger) {
        this.httpLogger = httpLogger;
        this.handler = new Handler(Looper.getMainLooper());
        this.state = new AtomicInteger();
        this.nextScheduled = new AtomicInteger();
        start();
    }

    private void schedule(long delayMillis) {
        int thisScheduled = nextScheduled.incrementAndGet();
        handler.postDelayed(() -> handle(thisScheduled), delayMillis);
    }

    void handle(int thisScheduled) {
        int state = this.state.get();
        if (state == STATE_STOPPED) {
            httpLogger.getFallback().debug("PeriodicLogsPublisher: we are already stopped");
            return;
        }

        httpLogger.sendToServer();
        if (state == STATE_RUNNING) {
            // only schedule the next run if there hasn't been another run scheduled already
            if (thisScheduled == nextScheduled.get()) {
                httpLogger.getFallback().debug("PeriodicLogsPublisher: scheduled next run");
                schedule(PERIOD_MILLIS);
            }
        } else if (state == STATE_STOPPING) {
            httpLogger.getFallback().debug("PeriodicLogsPublisher: stopped");
            this.state.set(STATE_STOPPED);
        }
    }

    void start() {
        httpLogger.getFallback().debug("PeriodicLogsPublisher: started running");
        state.set(STATE_RUNNING);
        schedule(INITIAL_MILLIS);
    }

    void pause() {
        httpLogger.getFallback().debug("PeriodicLogsPublisher: initiating pause");
        httpLogger.sendToServer();
        state.set(STATE_STOPPING);
        schedule(PAUSE_MILLIS);
    }

    void stop() {
        httpLogger.getFallback().debug("PeriodicLogsPublisher: set to stopped");
        state.set(STATE_STOPPED);
    }
}
