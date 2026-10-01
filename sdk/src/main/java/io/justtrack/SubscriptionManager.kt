package io.justtrack

internal class SubscriptionManager<L> {
    private var nextId = 1
    private val subscriptions: MutableMap<Int, L> = HashMap()

    // Deadlock-Safety: We only hold the lock for a short time to create an id and add the subscription
    @Synchronized
    fun subscribe(listener: L): Subscription {
        val currentId = nextId
        nextId++
        subscriptions[currentId] = listener
        return object : Subscription {
            override fun unsubscribe() {
                // Deadlock-Safety: We only hold the lock to remove an element from the map
                synchronized(this) {
                    subscriptions.remove(currentId)
                }
            }
        }
    }

    fun call(handler: SubscriptionHandler<L>) {
        val subscriptionList: List<L>
        // Deadlock-Safety: We only hold the lock to create a copy of a list
        synchronized(this) {
            // create a copy of all currently subscribed clients - we must not hold a lock while calling
            // untrusted code - if that code is modifying a subscription (on another thread) we would
            // cause a deadlock, having a local copy frees us from this problem
            subscriptionList = ArrayList(subscriptions.values)
        }
        for (subscription in subscriptionList) {
            handler.handle(subscription)
        }
    }
}
