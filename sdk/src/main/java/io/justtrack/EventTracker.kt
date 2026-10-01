package io.justtrack

internal interface EventTracker {
    fun track(event: AppEvent, sessionManager: SessionManager): AsyncFuture<Void?>
}
