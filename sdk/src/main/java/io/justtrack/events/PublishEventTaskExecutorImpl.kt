package io.justtrack.events

import io.justtrack.AsyncFuture
import io.justtrack.AttributionIdManager
import io.justtrack.AttributionOutputProvider
import io.justtrack.DeviceInfo
import io.justtrack.PublishEventsTask
import io.justtrack.RetryConfig
import io.justtrack.RetryingTask
import io.justtrack.StorableEvent
import io.justtrack.Task
import io.justtrack.TrackingEventErrorClassifier.Companion.instance
import io.justtrack.api.EventApi
import io.justtrack.api.EventApiImpl
import io.justtrack.executor.TaskExecutor
import io.justtrack.log.Logger
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.VersionBundle

internal class PublishEventTaskExecutorImpl(
    private val attributionParams: AttributionParams,
    private val attributionOutputProvider: AttributionOutputProvider,
    private val taskExecutor: TaskExecutor,
    private val retryConfig: RetryConfig,
    private val eventApi: EventApi,
    private val logger: Logger,
) : PublishEventTaskExecutor {
    override fun runPublishEventTask(events: List<StorableEvent>, eventSdkVersion: SdkVersion): AsyncFuture<List<StorableEvent>> {
        val publishEventsTask: Task<List<StorableEvent>> = PublishEventsTask(
            attributionParams.deviceInfo,
            events,
            PublishEventsTask.AttributionParams(
                attributionParams.advertiserIdProvider.provideAdvertiserId(),
                attributionOutputProvider.provideAttributionOutput(null),
                attributionParams.trackingId,
                attributionParams.trackingProvider,
                attributionParams.attributionIdManager.getOrCreateInstallId(),
            ),
            VersionBundle(eventSdkVersion, attributionParams.versionBundle.applicationVersion),
            this.eventApi,
        )
        val retryingTask: Task<List<StorableEvent>> = RetryingTask(
            publishEventsTask,
            attributionParams.deviceInfo,
            logger,
            retryConfig.publishEventsRetries,
            instance,
            EventApiImpl.SEND_USER_EVENTS_REQUEST_NAME,
        )

        return taskExecutor.executeFuture(retryingTask)
    }

    internal data class AttributionParams(
        internal val attributionIdManager: AttributionIdManager,
        internal val advertiserIdProvider: AdvertiserIdProvider,
        internal val trackingProvider: String,
        internal val trackingId: String?,
        internal val deviceInfo: DeviceInfo,
        internal val versionBundle: VersionBundle,
    )
}
