package io.justtrack

import io.justtrack.PublishEventsQueue.Companion.build
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.versions.VersionBundle
import java.util.UUID

internal class PublishEventsTask<T : List<PublishingEvent>>(
    private val deviceInfo: DeviceInfo,
    private val logger: HttpLogger,
    private val events: T,
    private val attributionParams: AttributionParams,
    private val versionBundle: VersionBundle,
    private val httpClient: HttpClient,
) : Task<T> {
    override suspend fun execute(): T {
        val advertiserIdValue = attributionParams.advertiserId.await().advertiserId
        val userId = attributionParams.userIdFuture.await()
        val installInstanceId = attributionParams.installInstanceIdFunction.invoke().await()

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

        val result = httpClient.sendUserEvents(
            logger,
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
        val userIdFuture: AsyncFuture<UUID>,
        val trackingId: String?,
        val trackingProvider: String,
        val installInstanceIdFunction: () -> AsyncFuture<String>,
    )
}
