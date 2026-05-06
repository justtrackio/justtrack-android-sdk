package io.justtrack;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SubscriptionManager<L> {
    private int nextId;
    @NonNull
    private final Map<Integer, L> subscriptions;

    SubscriptionManager() {
        nextId = 1;
        subscriptions = new HashMap<>();
    }

    @NonNull
    // Deadlock-Safety: We only hold the lock for a short time to create an id and add the subscription
    synchronized Subscription subscribe(@NonNull L listener) {
        int currentId = nextId;
        nextId++;
        subscriptions.put(currentId, listener);

        return () -> {
            // Deadlock-Safety: We only hold the lock to remove an element from the map
            synchronized (this) {
                subscriptions.remove(currentId);
            }
        };
    }

    void call(@NonNull SubscriptionHandler<L> handler) {
        final List<L> subscriptionList;
        // Deadlock-Safety: We only hold the lock to create a copy of a list
        synchronized (this) {
            // create a copy of all currently subscribed clients - we must not hold a lock while calling
            // untrusted code - if that code is modifying a subscription (on another thread) we would
            // cause a deadlock, having a local copy frees us from this problem
            subscriptionList = new ArrayList<>(subscriptions.values());
        }
        for (@NonNull L subscription : subscriptionList) {
            handler.handle(subscription);
        }
    }

    interface SubscriptionHandler<L> {
        void handle(@NonNull L listener);
    }
}
