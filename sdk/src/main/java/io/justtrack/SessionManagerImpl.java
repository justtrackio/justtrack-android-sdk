package io.justtrack;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.justtrack.crashes.CrashHandler;
import io.justtrack.events.JtSessionTrackingEvent;
import io.justtrack.events.TimeUnitGroup;

/**
 * Rule for this class: You must not call methods on the SDK while holding a lock for this class.
 * Otherwise, one thread can call something on this class which locks the SDK while another calls
 * something on this class while already having a lock on the SDK, causing them to deadlock.
 */
class SessionManagerImpl implements SessionManager, Runnable {
    static final String SESSION_PREF_NAME = "justtrack-session-manager";
    @NonNull
    private final WeakReference<EventTracker> eventTracker;
    @Nullable
    private Session session;
    @Nullable
    private String lastSessionId;
    @Nullable
    private Thread worker;
    @NonNull
    private final BlockingQueue<Event> queue;
    @NonNull
    private final SharedPreferences store;
    @NonNull
    private final AtomicBoolean isTracking;

    SessionManagerImpl(
            @NonNull EventTracker eventTracker,
            @NonNull CrashHandler crashHandler,
            @NonNull Context context,
            @NonNull AtomicBoolean isTracking
    ) {
        // this needs to be its own weak reference, we don't want to start reporting
        // sessions for other sdk instances if the sdk is restarted while we are still running
        this.eventTracker = new WeakReference<>(eventTracker);
        lastSessionId = null;
        worker = new Thread(this);
        worker.setUncaughtExceptionHandler(crashHandler.getUncaughtExceptionHandler());
        worker.setName("JustTrack_SessionManager_Worker");
        queue = new LinkedBlockingDeque<>();
        store = context.getSharedPreferences(SESSION_PREF_NAME, Context.MODE_PRIVATE);
        session = null;
        this.isTracking = isTracking;
    }

    @Override
    public void start() {
        @Nullable Session lastSession = Session.getCurrentSession(store);
        // end any pending sessions, even when they are quite old. it is important that we try to have
        // the raw data on the backend side as complete as possible
        if (lastSession != null) {
            JtSessionTrackingEvent endEvent = lastSession.end();
            eventTracker.get().track(endEvent, this);
            Session.remove(store);
        }

        if (worker != null) {
            worker.start();
        }
    }

    @Override
    public void onResume() {
        if (isTracking.get()) {
            queue.add(Event.START);
        }
    }

    @Override
    public void onPause() {
        if (isTracking.get()) {
            queue.add(Event.STOP);
        }
    }

    @Override
    @NonNull
    public String getLatestSessionId() {
        final JtSessionTrackingEvent startEvent;
        final String result;

        // Deadlock-Safety: We only update local state - we acquire the lock of the SDK only after
        // releasing the lock on this instance again.
        synchronized (this) {
            if (lastSessionId == null && session == null) {
                startEvent = startSession();
            } else {
                startEvent = null;
            }

            if (session != null) {
                result = session.sessionId.toString();
            } else {
                result = lastSessionId;
            }
        }

        if (startEvent != null) {
            final EventTracker eventTrackerRef = eventTracker.get();
            if (eventTrackerRef != null) {
                eventTrackerRef.track(startEvent, this);
            }
        }

        return result;
    }

    @Override
    public void shutdown() {
        final JtSessionTrackingEvent endEvent;

        // Deadlock-Safety: We only update local state - we acquire the lock of the SDK only after
        // releasing the lock on this instance again.
        synchronized (this) {
            if (worker != null) {
                worker.interrupt();
                worker = null;
            }

            endEvent = endSession();
        }

        if (endEvent != null) {
            final EventTracker eventTrackerRef = eventTracker.get();
            if (eventTrackerRef != null) {
                eventTrackerRef.track(endEvent, this);
            }
        }
    }

    @Override
    public void updateSessionTimeStamp() {
        refreshSession();
    }

    @NonNull
    private JtSessionTrackingEvent startSession() {
        session = new Session();
        session.persist(store);
        return new JtSessionTrackingEvent(session.sessionId.toString(), "start", new Date());
    }

    @Nullable
    private JtSessionTrackingEvent endSession() {
        if (session == null) {
            return null;
        }
        final JtSessionTrackingEvent endEvent = session.end(new Date());
        lastSessionId = session.sessionId.toString();
        Session.remove(store);
        session = null;

        return endEvent;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Event event = getNextEvent();
                @Nullable EventTracker eventTrackerRef = eventTracker.get();
                if (eventTrackerRef == null) {
                    // the SDK got destroyed, terminate
                    return;
                }
                if (event == Event.START) {
                    handleStartEvent(eventTrackerRef);
                } else {
                    handleStopEvent(eventTrackerRef);
                }
            } catch (InterruptedException exception) {
                // we are told to terminate
                return;
            }
        }
    }

    @NonNull
    private Event getNextEvent() throws InterruptedException {
        while (true) {
            if (session == null) {
                return queue.take();
            }

            @Nullable Event nextEvent = queue.poll(1, TimeUnit.MINUTES);
            if (nextEvent != null) {
                return nextEvent;
            }

            // we got no event for 1 minute and have an open session, update the saved timestamp
            // of that session so we know when we have to end a previous session
            refreshSession();
        }
    }

    private void handleStartEvent(@NonNull EventTracker eventTracker) {
        final JtSessionTrackingEvent startEvent;

        // Deadlock-Safety: We only update local state - we acquire the lock of the SDK only after
        // releasing the lock on this instance again.
        synchronized (this) {
            if (session != null) {
                return;
            }

            startEvent = startSession();
        }

        eventTracker.track(startEvent, this);
    }

    private void handleStopEvent(@NonNull EventTracker eventTracker) {
        final JtSessionTrackingEvent endEvent;

        // Deadlock-Safety: We only update local state - we acquire the lock of the SDK only after
        // releasing the lock on this instance again.
        synchronized (this) {
            endEvent = endSession();
        }

        if (endEvent != null) {
            eventTracker.track(endEvent, this);
        }
    }

    private void refreshSession() {
        // Deadlock-Safety: We are not using the SDK, so we don't need to (and can't as we don't have
        // a reference) lock it. The SessionManagerImpl is lower in the lock order than the SDK.
        synchronized (this) {
            if (session != null) {
                session.persist(store);
            }
        }
    }

    static class Session {
        private static final @NonNull String SESSION_KEY = "justtrack-session";

        @NonNull
        private final UUID sessionId;
        private final long sessionStart;
        private long sessionLastTick;

        private Session() {
            this(UUID.randomUUID(), System.currentTimeMillis(), System.currentTimeMillis());
        }

        Session(@NonNull UUID sessionId, long sessionStart, long sessionLastTick) {
            this.sessionId = sessionId;
            this.sessionStart = sessionStart;
            this.sessionLastTick = sessionLastTick;
        }

        private void persist(@NonNull SharedPreferences store) {
            sessionLastTick = System.currentTimeMillis();
            long sessionAge = sessionLastTick - sessionStart;
            SharedPreferencesKt.putString(store, SESSION_KEY, sessionId + ":" + sessionAge + ":" + sessionLastTick);
        }

        @Nullable
        static Session getCurrentSession(@NonNull SharedPreferences preferences) {
            @Nullable String sessionString = preferences.getString(SESSION_KEY, null);

            if (sessionString == null) {
                return null;
            }

            try {
                String[] parts = sessionString.split(":", 3);
                UUID sessionId = UUID.fromString(parts[0]);
                long sessionAge = Long.parseLong(parts[1]);
                long sessionLastTick = Long.parseLong(parts[2]);

                return new Session(sessionId, sessionLastTick - sessionAge, sessionLastTick);
            } catch (Throwable exception) {
                return null;
            }
        }

        static void remove(@NonNull SharedPreferences preferences) {
            SharedPreferencesKt.remove(preferences, SESSION_KEY);
        }

        JtSessionTrackingEvent end() {
            return end(new Date(sessionLastTick));
        }

        JtSessionTrackingEvent end(@NonNull Date now) {
            return new JtSessionTrackingEvent(sessionId.toString(), "end", (double) (now.getTime() - sessionStart), TimeUnitGroup.MILLISECONDS, now);
        }

        @VisibleForTesting
        static void clearForTesting(@NonNull Context context) {
            SharedPreferencesKt.getSharePrefWithIO(context, SESSION_KEY, Context.MODE_PRIVATE, preferences -> {
                preferences.edit().clear().apply();

                return null;
            });
        }
    }

    private enum Event {
        START,
        STOP
    }
}
