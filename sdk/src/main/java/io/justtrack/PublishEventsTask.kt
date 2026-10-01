package io.justtrack

import io.justtrack.PublishEventsQueue.Companion.build
import io.justtrack.api.EventApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.versions.VersionBundle
import java.util.UUID

internal class PublishEventsTask<T : List<StorableEvent>>(
    private val deviceInfo: DeviceInfo,
    private val events: T,
    private val attributionParams: AttributionParams,
    private val versionBundle: VersionBundle,
    private val eventApi: EventApi,
) : Task<T> {
    override suspend fun execute(): T {
        val advertiserIdValue = attributionParams.advertiserId.await().advertiserId
        val attributionResponse = attributionParams.attributionOutput.await().getAttributionResponse()
        val installInstanceId = attributionParams.installInstanceId.await()

        val userId = attributionResponse.getUserId()
        val event = build(
            events,
            deviceInfo,
            PublishEventsQueue.DTOBuildAttributionParams(
                advertiserIdValue,
                attributionParams.trackingId,
                attributionParams.trackingProvider,
                userId,
                UUID.fromString(installInstanceId),
            ),
            versionBundle.sdkVersion,
            versionBundle.applicationVersion,
        )

        val result = eventApi.sendUserEvents(
            event,
            advertiserIdValue,
            userId.toString(),
            installInstanceId,
        )

        if (result.isSuccess) {
            return events
        } else {
            throw result.exceptionOrNull()
                ?: IllegalStateException("Publishing events failed with unknown exception")
        }
    }

    internal data class AttributionParams(
        val advertiserId: AsyncFuture<AdvertiserIdInfo>,
        val attributionOutput: AsyncFuture<AttributionOutput>,
        val trackingId: String?,
        val trackingProvider: String,
        val installInstanceId: AsyncFuture<String>,
    )
}
