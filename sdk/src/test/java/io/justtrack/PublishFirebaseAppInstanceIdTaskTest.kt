package io.justtrack

import android.content.Context
import io.justtrack.api.AttributionApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.attribution.AdvertiserIdInfoImpl
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
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
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class PublishFirebaseAppInstanceIdTaskTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var logger: RecordingLogger
    private lateinit var loggerParams: PublishFirebaseAppInstanceIdTask.LoggerParams
    private val nextFid = AtomicInteger(0)

    @Before
    fun setUp() {
        FirebaseIdStore.getInstance().clearForTesting(context)
        logger = RecordingLogger()
        loggerParams = PublishFirebaseAppInstanceIdTask.LoggerParams(logger, NetworkErrorLogger())
    }

    private class RecordingLogger : io.justtrack.log.Logger {
        val infoLogs = mutableListOf<String>()
        val warnLogs = mutableListOf<Pair<String, Throwable?>>()
        override val fallback: io.justtrack.log.Logger get() = this
        override fun debug(message: String, vararg fields: io.justtrack.log.LoggerFields) = Unit
        override fun info(message: String, vararg fields: io.justtrack.log.LoggerFields) {
            infoLogs.add(message)
        }
        override fun warn(message: String, vararg fields: io.justtrack.log.LoggerFields) {
            warnLogs.add(message to null)
        }
        override fun warn(message: String, exception: Throwable, vararg fields: io.justtrack.log.LoggerFields) {
            warnLogs.add(message to exception)
        }
        override fun error(message: String, vararg fields: io.justtrack.log.LoggerFields) = Unit
        override fun error(message: String, exception: Throwable, vararg fields: io.justtrack.log.LoggerFields) = Unit
        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: io.justtrack.log.LoggerFields) = Unit
    }

    @After
    fun tearDown() {
        FirebaseIdStore.getInstance().clearForTesting(context)
    }

    private fun uniqueFirebaseId(): String = "fid-${nextFid.incrementAndGet()}-${UUID.randomUUID()}"

    private fun futureOf(value: String): AsyncFuture<String> = ResolvableFuture<String>().also { it.resolve(value) }
    private fun futureOfInfo(info: AdvertiserIdInfo): AsyncFuture<AdvertiserIdInfo> = ResolvableFuture<AdvertiserIdInfo>().also { it.resolve(info) }

    private fun mockAttributionIdManager(installId: String): AttributionIdManager {
        val mgr = mock(AttributionIdManager::class.java)
        whenever(mgr.getOrCreateInstallId()).thenReturn(ResolvableFuture<String>().also { it.resolve(installId) })
        return mgr
    }

    @Test
    fun `returns false when there is no pending firebase id`() = runBlocking {
        val installId = "install-${UUID.randomUUID()}"
        val firebaseId = uniqueFirebaseId()
        val params = PublishFirebaseAppInstanceIdTask.AttributionParams(
            userIdFuture = futureOf("user-1"),
            advertiserIdProvider = AdvertiserIdProvider { futureOfInfo(AdvertiserIdInfoImpl(UUID.randomUUID(), false)) },
            firebaseId = firebaseId,
        )
        val task = newTask(installId, params, RecordingAttributionApi(), reason = "test")
        assertFalse(task.execute())
    }

    @Test
    fun `success path stores id and logs`() = runBlocking {
        val installId = "install-${UUID.randomUUID()}"
        val firebaseId = uniqueFirebaseId()
        FirebaseIdStore.getInstance().storeNewId(context, installId, firebaseId)
        assertEquals(firebaseId, FirebaseIdStore.getInstance().getPendingId(context))

        val recAdvId = UUID.randomUUID()
        val api = RecordingAttributionApi()
        val params = PublishFirebaseAppInstanceIdTask.AttributionParams(
            userIdFuture = futureOf("user-A"),
            advertiserIdProvider = AdvertiserIdProvider { futureOfInfo(AdvertiserIdInfoImpl(recAdvId, false)) },
            firebaseId = firebaseId,
        )
        val task = newTask(installId, params, api, reason = "send")

        assertTrue(task.execute())
        assertEquals(1, api.firebaseCalls.size)
        val call = api.firebaseCalls.single()
        assertEquals(recAdvId.toString().lowercase(), call.advertiserId)
        assertEquals("user-A", call.uuid)
        assertEquals(installId, call.installId)
        assertNull(FirebaseIdStore.getInstance().getPendingId(context))
        assertTrue(logger.infoLogs.any { it.contains("Publishing new Firebase app instance id") })
    }

    @Test
    fun `failure result rejects future and returns true`() = runBlocking {
        val installId = "install-${UUID.randomUUID()}"
        val firebaseId = uniqueFirebaseId()
        FirebaseIdStore.getInstance().storeNewId(context, installId, firebaseId)

        val ex = IOException("backend down")
        val api = RecordingAttributionApi(firebaseResult = Result.failure(ex))
        val params = PublishFirebaseAppInstanceIdTask.AttributionParams(
            userIdFuture = futureOf("user-B"),
            advertiserIdProvider = AdvertiserIdProvider { futureOfInfo(AdvertiserIdInfoImpl(UUID.randomUUID(), false)) },
            firebaseId = firebaseId,
        )
        val task = newTask(installId, params, api, reason = "send")
        assertTrue(task.execute())
        assertNotNull(FirebaseIdStore.getInstance().getPendingId(context))
    }

    @Test
    fun `network exception logs info and rethrows`() = runBlocking {
        val installId = "install-${UUID.randomUUID()}"
        val firebaseId = uniqueFirebaseId()
        FirebaseIdStore.getInstance().storeNewId(context, installId, firebaseId)

        val ex = UnknownHostException("no dns")
        val api = RecordingAttributionApi(firebaseThrows = ex)
        val params = PublishFirebaseAppInstanceIdTask.AttributionParams(
            userIdFuture = futureOf("user-C"),
            advertiserIdProvider = AdvertiserIdProvider { futureOfInfo(AdvertiserIdInfoImpl(UUID.randomUUID(), false)) },
            firebaseId = firebaseId,
        )
        val task = newTask(installId, params, api, reason = "send")
        try {
            task.execute()
            fail("expected throw")
        } catch (caught: Throwable) {
            assertSame(ex, caught)
        }
        assertTrue(logger.infoLogs.any { it.contains("Failed to publish new Firebase app instance id") })
    }

    @Test
    fun `non-network exception logs warn and rethrows`() = runBlocking {
        val installId = "install-${UUID.randomUUID()}"
        val firebaseId = uniqueFirebaseId()
        FirebaseIdStore.getInstance().storeNewId(context, installId, firebaseId)

        val ex = IllegalStateException("bad state")
        val api = RecordingAttributionApi(firebaseThrows = ex)
        val params = PublishFirebaseAppInstanceIdTask.AttributionParams(
            userIdFuture = futureOf("user-D"),
            advertiserIdProvider = AdvertiserIdProvider { futureOfInfo(AdvertiserIdInfoImpl(UUID.randomUUID(), false)) },
            firebaseId = firebaseId,
        )
        val task = newTask(installId, params, api, reason = "send")
        try {
            task.execute()
            fail("expected throw")
        } catch (caught: Throwable) {
            assertSame(ex, caught)
        }
        assertTrue(logger.warnLogs.any { it.first.contains("Failed to publish new Firebase app instance id") })
    }

    @Test
    fun `concurrent duplicate request short-circuits via RunningPublishRequests`() = runBlocking {
        val installId = "install-${UUID.randomUUID()}"
        val firebaseId = uniqueFirebaseId()
        FirebaseIdStore.getInstance().storeNewId(context, installId, firebaseId)

        // Hook a callback that resolves only AFTER it has been registered as awaiter,
        // so the in-task `existingRequest.await()` is what triggers resolution.
        val priorFuture = ResolvableFuture<Unit>()
        val priorOffer = RunningPublishRequests.offerFirebaseAppInstanceId(installId, firebaseId, priorFuture)
        assertNull(priorOffer)

        val params = PublishFirebaseAppInstanceIdTask.AttributionParams(
            userIdFuture = futureOf("user-E"),
            advertiserIdProvider = AdvertiserIdProvider { futureOfInfo(AdvertiserIdInfoImpl(UUID.randomUUID(), false)) },
            firebaseId = firebaseId,
        )
        val api = RecordingAttributionApi()
        val task = newTask(installId, params, api, reason = "send")

        // Run the task on a background thread, then resolve from the main thread once it has begun.
        val thread = Thread { runBlocking { assertTrue(task.execute()) } }
        thread.start()
        Thread.sleep(50) // let the coroutine reach existingRequest.await()
        priorFuture.resolve(Unit)
        thread.join(2000)
        assertFalse(thread.isAlive)
        assertEquals(0, api.firebaseCalls.size)
    }

    private fun newTask(
        installId: String,
        params: PublishFirebaseAppInstanceIdTask.AttributionParams,
        attributionApi: AttributionApi,
        reason: String,
    ): PublishFirebaseAppInstanceIdTask = PublishFirebaseAppInstanceIdTask(
        context = context,
        attributionIdManager = mockAttributionIdManager(installId),
        loggerParams = loggerParams,
        attributionApi = attributionApi,
        attributionParams = params,
        reason = reason,
    )

    private data class FirebaseCall(val advertiserId: String?, val uuid: String, val installId: String)

    private class RecordingAttributionApi(
        private val firebaseResult: Result<Unit> = Result.success(Unit),
        private val firebaseThrows: Throwable? = null,
    ) : AttributionApi {
        val firebaseCalls = mutableListOf<FirebaseCall>()
        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> = Result.success(null)
        override suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject> = Result.success(JSONObject())
        override suspend fun sendCustomUserId(
            body: DTOPublishCustomUserIdRequest,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> = Result.success(Unit)
        override suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit> {
            firebaseCalls.add(FirebaseCall(advertiserId, uuid, installId))
            firebaseThrows?.let { throw it }
            return firebaseResult
        }
    }
}
