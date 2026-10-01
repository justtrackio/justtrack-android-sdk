package io.justtrack

import io.justtrack.api.EventApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.attribution.Campaign
import io.justtrack.attribution.Channel
import io.justtrack.attribution.Partner
import io.justtrack.dtos.DTOAppEvent
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersionImpl
import io.justtrack.versions.VersionBundle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date
import java.util.TreeMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import io.justtrack.events.Unit as EventUnit

@RunWith(RobolectricTestRunner::class)
class PublishEventsTaskTest {

    private val deviceInfo = TestDeviceInfoImpl()
    private val versionBundle = VersionBundle(
        SdkVersionImpl(5, 6, 7, "5.6.7", PlatformType.ANDROID),
        ApplicationVersionImpl("7.0.0", "7000"),
    )

    // ---------- success ----------

    @Test
    fun execute_success_returnsOriginalEventsList_andCallsEventApiWithExpectedIds() {
        val userId = UUID.randomUUID()
        val installInstanceId = UUID.randomUUID().toString()
        val response = TestAttributionResponse(userId, "install-id")
        val output = AttributionOutput(response, null, false)
        val events = listOf(publishingEvent("login"), publishingEvent("purchase"))
        val api = RecordingEventApi(Result.success(JSONObject()))

        val task = PublishEventsTask(
            deviceInfo,
            events,
            attributionParams(
                advertiserId = "ad-1",
                attributionOutput = output,
                installInstanceId = installInstanceId,
                trackingId = "track-1",
                trackingProvider = "provider-1",
            ),
            versionBundle,
            api,
        )

        val result = runBlocking { task.execute() }

        assertSame(events, result)
        assertEquals(1, api.callCount.get())
        assertEquals("ad-1", api.lastAdvertiserId)
        assertEquals(userId.toString(), api.lastUuid)
        assertEquals(installInstanceId, api.lastInstallId)
        // sanity check that build() actually produced a non-null DTO
        assertNull(api.lastBody?.let { null }) // body is non-null
        assertTrue(api.lastBody != null)
    }

    @Test
    fun execute_success_withNullAdvertiserIdAndNullTrackingId_propagatesNulls() {
        val userId = UUID.randomUUID()
        val output = AttributionOutput(TestAttributionResponse(userId, "iid"), null, false)
        val api = RecordingEventApi(Result.success(JSONObject()))
        val task = PublishEventsTask(
            deviceInfo,
            listOf(publishingEvent("e")),
            attributionParams(
                advertiserId = null,
                attributionOutput = output,
                installInstanceId = UUID.randomUUID().toString(),
                trackingId = null,
                trackingProvider = "prov",
            ),
            versionBundle,
            api,
        )

        runBlocking { task.execute() }

        assertNull(api.lastAdvertiserId)
        assertEquals(userId.toString(), api.lastUuid)
    }

    @Test
    fun execute_success_emptyEventsList_returnsEmpty() {
        val output = AttributionOutput(TestAttributionResponse(UUID.randomUUID(), "iid"), null, false)
        val api = RecordingEventApi(Result.success(JSONObject()))
        val events = emptyList<StorableEvent>()
        val task = PublishEventsTask(
            deviceInfo,
            events,
            attributionParams("a", output, UUID.randomUUID().toString(), null, "p"),
            versionBundle,
            api,
        )

        val out = runBlocking { task.execute() }

        assertSame(events, out)
        assertEquals(1, api.callCount.get())
    }

    // ---------- failure ----------

    @Test
    fun execute_failure_rethrowsExceptionFromResult() {
        val cause = IllegalStateException("publish exploded")
        val output = AttributionOutput(TestAttributionResponse(UUID.randomUUID(), "iid"), null, false)
        val api = RecordingEventApi(Result.failure(cause))
        val task = PublishEventsTask(
            deviceInfo,
            listOf(publishingEvent("e")),
            attributionParams("a", output, UUID.randomUUID().toString(), null, "p"),
            versionBundle,
            api,
        )

        val thrown = assertThrows(IllegalStateException::class.java) { runBlocking { task.execute() } }
        assertSame(cause, thrown)
    }

    @Test
    fun execute_failure_runtimeException_propagates() {
        val cause = RuntimeException("io")
        val output = AttributionOutput(TestAttributionResponse(UUID.randomUUID(), "iid"), null, false)
        val api = RecordingEventApi(Result.failure(cause))
        val task = PublishEventsTask(
            deviceInfo,
            listOf(publishingEvent("e")),
            attributionParams("a", output, UUID.randomUUID().toString(), null, "p"),
            versionBundle,
            api,
        )

        val thrown = assertThrows(RuntimeException::class.java) { runBlocking { task.execute() } }
        assertSame(cause, thrown)
    }

    // ---------- AttributionParams data class ----------

    @Test
    fun attributionParams_dataClass_equality() {
        val advertiserFuture: AsyncFuture<AdvertiserIdInfo> = ValueFuture(TestAdvertiserIdInfo("a"))
        val output = AttributionOutput(TestAttributionResponse(UUID.randomUUID(), "iid"), null, false)
        val outputFuture = ValueFuture(output)
        val installFuture = ValueFuture("iid")
        val a = PublishEventsTask.AttributionParams(advertiserFuture, outputFuture, "t", "p", installFuture)
        val b = PublishEventsTask.AttributionParams(advertiserFuture, outputFuture, "t", "p", installFuture)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ---------- helpers ----------

    private fun attributionParams(
        advertiserId: String?,
        attributionOutput: AttributionOutput,
        installInstanceId: String,
        trackingId: String?,
        trackingProvider: String,
    ): PublishEventsTask.AttributionParams {
        val adFuture: AsyncFuture<AdvertiserIdInfo> = ValueFuture(TestAdvertiserIdInfo(advertiserId))
        return PublishEventsTask.AttributionParams(
            advertiserId = adFuture,
            attributionOutput = ValueFuture(attributionOutput),
            trackingId = trackingId,
            trackingProvider = trackingProvider,
            installInstanceId = ValueFuture(installInstanceId),
        )
    }

    private fun publishingEvent(name: String): StorableEvent {
        val event = PublishableAppEvent(
            name,
            TreeMap(mapOf("k" to "v")),
            1.0,
            EventUnit.COUNT,
            null,
            "session-id",
            SdkVersionImpl(5, 6, 7, "5.6.7", PlatformType.ANDROID),
            Date(),
        )
        return StorableEvent(eventId = UUID.randomUUID(), event = event)
    }

    private class RecordingEventApi(private val result: Result<JSONObject?>) : EventApi {
        var lastBody: DTOAppEvent? = null
        var lastAdvertiserId: String? = null
        var lastUuid: String? = null
        var lastInstallId: String? = null
        val callCount = AtomicInteger(0)

        override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
            callCount.incrementAndGet()
            lastBody = body
            lastAdvertiserId = advertiserId
            lastUuid = uuid
            lastInstallId = installId
            return result
        }
    }

    private class TestAdvertiserIdInfo(override val advertiserId: String?) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }

    private class TestAttributionResponse(
        private val userId: UUID,
        private val installId: String,
    ) : AttributionResponse {
        override fun getUserId(): UUID = userId
        override fun getInstallId(): String = installId
        override fun getUserType(): String = "acquisition"
        override fun getCampaign(): Campaign = TestCampaign()
        override fun getChannel(): Channel = TestChannel()
        override fun getPartner(): Partner = TestPartner()
        override fun getSourceId(): String? = null
        override fun getSourceBundleId(): String? = null
        override fun getSourcePlacement(): String? = null
        override fun getAdsetId(): String? = null
        override fun getCreatedAt(): Date = Date(0)
        override fun getRedownload(): Boolean = false
    }

    private class TestCampaign : Campaign {
        override val id: String = "1"
        override val name: String = "campaign"
        override val type: String = "acquisition"
        override val isOrganic: Boolean = false
    }

    private class TestChannel : Channel {
        override val id: Int = 2
        override val name: String = "channel"
        override val isIncent: Boolean = false
    }

    private class TestPartner : Partner {
        override val id: Int = 3
        override val name: String = "partner"
    }
}
