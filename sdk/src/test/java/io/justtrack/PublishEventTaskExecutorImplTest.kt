package io.justtrack

import io.justtrack.api.EventApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.attribution.Campaign
import io.justtrack.attribution.Channel
import io.justtrack.attribution.Partner
import io.justtrack.dtos.DTOAppEvent
import io.justtrack.events.PublishEventTaskExecutorImpl
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.VersionBundle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.UUID

internal class PublishEventTaskExecutorImplTest {
    private val taskExecutor = ImmediateSyncTaskExecutor()
    private val eventApi: EventApi = mock()
    private val attributionIdManager: AttributionIdManager = mock()
    private val attributionOutputProvider: AttributionOutputProvider = mock()
    private val sdkVersion = TestSdkVersion("1.2.3-event")
    private val applicationVersion = TestApplicationVersion("4.5.6", "456")
    private val attributionResponse = TestAttributionResponse(
        userId = UUID.fromString("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e"),
        installId = "install-id",
    )

    private val advertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo("advertiser-id")) }

    private val attributionParams = PublishEventTaskExecutorImpl.AttributionParams(
        attributionIdManager = attributionIdManager,
        advertiserIdProvider = advertiserIdProvider,
        trackingProvider = "tracking-provider",
        trackingId = "tracking-id",
        deviceInfo = TestDeviceInfoImpl(),
        versionBundle = VersionBundle(TestSdkVersion("original-sdk-version"), applicationVersion),
    )
    private val executor = PublishEventTaskExecutorImpl(
        attributionParams,
        attributionOutputProvider,
        taskExecutor,
        RetryConfig(0, 0, 0, emptyList()),
        eventApi,
        TestLogger(),
    )

    @Test
    fun runPublishEventTask_executesPublishTaskAndReturnsEvents() {
        whenever(attributionOutputProvider.provideAttributionOutput(null)).thenReturn(ValueFuture(attributionOutput()))
        whenever(attributionIdManager.getOrCreateInstallId()).thenReturn(ValueFuture("f7642b2f-35e9-4a2f-9c26-a5f9cd2b4794"))
        runBlocking {
            whenever(eventApi.sendUserEvents(any(), any(), any(), any())).thenReturn(Result.success(JSONObject()))
        }
        val events = listOf(testPublishingEvent())

        val result = executor.runPublishEventTask(events, sdkVersion)

        assertSame(events, result.get())
        assertEquals(1, taskExecutor.executeCount.get())
    }

    @Test
    fun runPublishEventTask_usesAttributionParamsAndEventSdkVersion() {
        whenever(attributionOutputProvider.provideAttributionOutput(null)).thenReturn(ValueFuture(attributionOutput()))
        whenever(attributionIdManager.getOrCreateInstallId()).thenReturn(ValueFuture("f7642b2f-35e9-4a2f-9c26-a5f9cd2b4794"))
        runBlocking {
            whenever(eventApi.sendUserEvents(any(), any(), any(), any())).thenReturn(Result.success(JSONObject()))
        }

        executor.runPublishEventTask(listOf(testPublishingEvent()), sdkVersion).get()

        val bodyCaptor = argumentCaptor<DTOAppEvent>()
        runBlocking {
            verify(eventApi).sendUserEvents(
                bodyCaptor.capture(),
                eq("advertiser-id"),
                eq("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e"),
                eq("f7642b2f-35e9-4a2f-9c26-a5f9cd2b4794"),
            )
        }

        val body = bodyCaptor.firstValue
        val json = body.toJSON(Formatter)
        assertEquals("advertiser-id", json.getJSONObject("user").getString("deviceId"))
        assertEquals("8a4929d4-b3f4-4593-84f9-b2fad0e9cc1e", json.getJSONObject("user").getString("userId"))
        assertEquals("f7642b2f-35e9-4a2f-9c26-a5f9cd2b4794", json.getJSONObject("user").getString("installInstanceId"))
        assertEquals("4.5.6", json.getJSONObject("appVersion").getString("name"))
        assertEquals("456", json.getJSONObject("appVersion").getString("code"))
        assertEquals("1.2.3-event", json.getJSONObject("sdkVersion").getString("name"))
    }

    private fun testPublishingEvent(): StorableEvent {
        val event = AppEvent("test_event")
            .build("session-id", sdkVersion)
        return StorableEvent(1, UUID.fromString("4bb77cbf-86b5-4e54-847a-74d31ec097a9"), event, 2)
    }

    private fun attributionOutput(): AttributionOutput = AttributionOutput(attributionResponse, null, false)

    private class TestSdkVersion(
        override val name: String,
    ) : SdkVersion {
        override val platformType: PlatformType = PlatformType.ANDROID
        override val major: Int = 1
        override val minor: Int = 2
        override val patch: Int = 3
    }

    private class TestApplicationVersion(
        private val name: String,
        private val code: String,
    ) : ApplicationVersion {
        override fun getVersionName(): String = name
        override fun getVersionCode(): String = code
    }

    private class TestAdvertiserIdInfo(
        override val advertiserId: String?,
    ) : AdvertiserIdInfo {
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
