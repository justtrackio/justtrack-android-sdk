package io.justtrack.events

import io.justtrack.AsyncFuture
import io.justtrack.StorableEvent
import io.justtrack.versions.SdkVersion

internal fun interface PublishEventTaskExecutor {
    fun runPublishEventTask(events: List<StorableEvent>, eventSdkVersion: SdkVersion): AsyncFuture<List<StorableEvent>>
}
