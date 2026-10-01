package io.justtrack

import android.content.Context
import io.justtrack.api.AttributionApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.attribution.AdvertiserIdInfoImpl
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.providers.AdvertiserIdProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
internal class PublishCustomUserIdTaskTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var logger: Logger
    private val nextId = AtomicInteger(0)

    @Before
    fun setUp() {
        CustomUserIdStore.getInstance().clearForTesting(context)
        logger = mock()
    }

    @After
    fun tearDown() {
        CustomUserIdStore.getInstance().clearForTesting(context)
    }

    @Test
    fun `returns true without publishing when there is no pending custom user id`() = runBlocking {
        val installId = uniqueInstallId()
        val customUserId = uniqueCustomUserId()
        val api = RecordingAttributionApi()
        val task = newTask(installId, customUserId, api)

        assertTrue(task.execute())

        assertTrue(api.customUserIdCalls.isEmpty())
    }

    @Test
    fun `success path sends body stores backend id and logs`() = runBlocking {
        val installId = uniqueInstallId()
        val customUserId = uniqueCustomUserId()
        CustomUserIdStore.getInstance().storeNewId(context, installId, customUserId)
        val advertiserId = UUID.randomUUID()
        val api = RecordingAttributionApi()
        val task = newTask(installId, customUserId, api, advertiserId = advertiserId, userId = "user-A", reason = "send")

        assertTrue(task.execute())

        val call = api.customUserIdCalls.single()
        assertEquals(installId, call.body.installId)
        assertEquals(customUserId, call.body.customUserId)
        assertEquals(advertiserId.toString().lowercase(), call.advertiserId)
        assertEquals("user-A", call.uuid)
        assertEquals(installId, call.installId)
        assertNull(CustomUserIdStore.getInstance().getPendingId(context))
        assertFalse(CustomUserIdStore.getInstance().storeNewId(context, installId, customUserId))
        verify(logger).info(eq("Publishing new custom user id"), any<LoggerFields>())
    }

    @Test
    fun `failure result keeps pending id and returns true`() = runBlocking {
        val installId = uniqueInstallId()
        val customUserId = uniqueCustomUserId()
        CustomUserIdStore.getInstance().storeNewId(context, installId, customUserId)
        val ex = IOException("backend down")
        val api = RecordingAttributionApi(customUserIdResult = Result.failure(ex))
        val task = newTask(installId, customUserId, api)

        assertTrue(task.execute())

        assertNotNull(CustomUserIdStore.getInstance().getPendingId(context))
    }

    @Test
    fun `network exception logs info rejects running request and rethrows`() = runBlocking {
        val installId = uniqueInstallId()
        val customUserId = uniqueCustomUserId()
        CustomUserIdStore.getInstance().storeNewId(context, installId, customUserId)
        val ex = UnknownHostException("no dns")
        val api = RecordingAttributionApi(customUserIdThrows = ex)
        val task = newTask(installId, customUserId, api)

        try {
            task.execute()
            fail("expected throw")
        } catch (caught: Throwable) {
            assertSame(ex, caught)
        }

        verify(logger).info(eq("Failed to publish custom user id ${ex.message}"), any<LoggerFields>())
        assertNull(RunningPublishRequests.offerCustomUserId(installId, customUserId, ResolvableFuture()))
    }

    @Test
    fun `non-network exception logs warn rejects running request and rethrows`() = runBlocking {
        val installId = uniqueInstallId()
        val customUserId = uniqueCustomUserId()
        CustomUserIdStore.getInstance().storeNewId(context, installId, customUserId)
        val ex = IllegalStateException("bad state")
        val api = RecordingAttributionApi(customUserIdThrows = ex)
        val task = newTask(installId, customUserId, api)

        try {
            task.execute()
            fail("expected throw")
        } catch (caught: Throwable) {
            assertSame(ex, caught)
        }

        verify(logger).warn(eq("Failed to publish custom user id ${ex.message}"), same(ex))
        assertNull(RunningPublishRequests.offerCustomUserId(installId, customUserId, ResolvableFuture()))
    }

    @Test
    fun `concurrent duplicate request short-circuits via RunningPublishRequests`() = runBlocking {
        val installId = uniqueInstallId()
        val customUserId = uniqueCustomUserId()
        CustomUserIdStore.getInstance().storeNewId(context, installId, customUserId)
        val priorFuture = ResolvableFuture<Unit>()
        assertNull(RunningPublishRequests.offerCustomUserId(installId, customUserId, priorFuture))
        val api = RecordingAttributionApi()
        val task = newTask(installId, customUserId, api)

        val thread = Thread { runBlocking { assertTrue(task.execute()) } }
        thread.start()
        Thread.sleep(50)
        priorFuture.resolve(Unit)
        thread.join(2_000)

        assertFalse(thread.isAlive)
        assertTrue(api.customUserIdCalls.isEmpty())
    }

    @Test
    fun `duplicate request propagates existing failure`() = runBlocking {
        val installId = uniqueInstallId()
        val customUserId = uniqueCustomUserId()
        CustomUserIdStore.getInstance().storeNewId(context, installId, customUserId)
        val priorFuture = ResolvableFuture<Unit>()
        assertNull(RunningPublishRequests.offerCustomUserId(installId, customUserId, priorFuture))
        val ex = IOException("prior failed")
        val api = RecordingAttributionApi()
        val task = newTask(installId, customUserId, api)
        val caughtRef = arrayOfNulls<Throwable>(1)

        val thread = Thread {
            runBlocking {
                try {
                    task.execute()
                } catch (caught: Throwable) {
                    caughtRef[0] = caught
                }
            }
        }
        thread.start()
        Thread.sleep(50)
        priorFuture.reject(ex)
        thread.join(2_000)

        assertFalse(thread.isAlive)
        assertSame(ex, caughtRef[0]?.cause)
        assertTrue(api.customUserIdCalls.isEmpty())
    }

    private fun newTask(
        installId: String,
        customUserId: String,
        attributionApi: AttributionApi,
        advertiserId: UUID? = UUID.randomUUID(),
        userId: String = "user-id",
        reason: String = "send",
    ): PublishCustomUserIdTask {
        return PublishCustomUserIdTask(
            context = context,
            attributionIdManager = mockAttributionIdManager(installId),
            logger = logger,
            networkErrorLogger = NetworkErrorLogger(),
            attributionApi = attributionApi,
            reason = reason,
            attrParams = PublishCustomUserIdTask.AttributionParams(
                customUserId = customUserId,
                userIdFuture = futureOf(userId),
                advertiserIdProvider = AdvertiserIdProvider { futureOfInfo(AdvertiserIdInfoImpl(advertiserId, false)) },
            ),
        )
    }

    private fun mockAttributionIdManager(installId: String): AttributionIdManager {
        val manager = mock<AttributionIdManager>()
        whenever(manager.getOrCreateInstallId()).thenReturn(futureOf(installId))
        return manager
    }

    private fun uniqueInstallId(): String = "install-${UUID.randomUUID()}"

    private fun uniqueCustomUserId(): String = "custom-${nextId.incrementAndGet()}-${UUID.randomUUID()}"

    private fun futureOf(value: String): AsyncFuture<String> = ResolvableFuture<String>().also { it.resolve(value) }

    private fun futureOfInfo(info: AdvertiserIdInfo): AsyncFuture<AdvertiserIdInfo> = ResolvableFuture<AdvertiserIdInfo>().also { it.resolve(info) }

    private data class CustomUserIdCall(
        val body: DTOPublishCustomUserIdRequest,
        val advertiserId: String?,
        val uuid: String,
        val installId: String,
    )

    private class RecordingAttributionApi(
        private val customUserIdResult: Result<Unit> = Result.success(Unit),
        private val customUserIdThrows: Throwable? = null,
    ) : AttributionApi {
        val customUserIdCalls = mutableListOf<CustomUserIdCall>()

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> = Result.success(null)

        override suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject> = Result.success(JSONObject())

        override suspend fun sendCustomUserId(
            body: DTOPublishCustomUserIdRequest,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> {
            customUserIdCalls.add(CustomUserIdCall(body, advertiserId, uuid, installId))
            customUserIdThrows?.let { throw it }
            return customUserIdResult
        }

        override suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit> {
            return Result.success(Unit)
        }
    }
}
