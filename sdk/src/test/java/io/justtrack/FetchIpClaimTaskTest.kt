package io.justtrack

import io.justtrack.api.AttributionApi
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import io.justtrack.providers.AdvertiserIdProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class FetchIpClaimTaskTest {

    private val deviceInfo = TestDeviceInfoImpl()

    // ---------- success ----------

    @Test
    fun execute_success_parsesResponse_publishesMetric_logsDebug_returnsToken() {
        val response = JSONObject()
            .put("ip", "1.2.3.4")
            .put("type", "v4")
            .put("token", "TOKEN-IPv4")
        val api = RecordingAttributionApi(Result.success(response))
        val logger = RecordingLogger()
        val task = FetchIpClaimTask(deviceInfo, advertiser("ad-123"), api, logger, IPProtocol.IPv4)

        val token = runBlocking { task.execute() }

        assertEquals("TOKEN-IPv4", token)
        // api invoked with correct protocol + advertiserId
        assertEquals(IPProtocol.IPv4, api.lastProtocol)
        assertEquals("ad-123", api.lastAdvertiserId)
        // metric published with protocol-specific metric and Network dimension
        assertEquals(1, logger.metrics.size)
        val metricCall = logger.metrics[0]
        assertSame(IPProtocol.IPv4.claimDurationMetric, metricCall.metric)
        assertTrue("value should be >= 0", metricCall.value >= 0.0)
        val networkField = metricCall.dimensions
            .flatMap { it.fields.entries }
            .firstOrNull { it.key == "Network" }
        assertNotNull(networkField)
        assertEquals(deviceInfo.getConnectionType().toString(), networkField!!.value)
        // debug log entry
        val debug = logger.debugs.single()
        assertEquals("Got IP claim", debug.message)
        val fields = debug.fields.flatMap { it.fields.entries }.associate { it.key to it.value }
        assertEquals("1.2.3.4", fields["ip"])
        assertEquals("v4", fields["type"])
    }

    @Test
    fun execute_success_withIPv6_usesIPv6Protocol() {
        val response = JSONObject()
            .put("ip", "::1")
            .put("type", "v6")
            .put("token", "T6")
        val api = RecordingAttributionApi(Result.success(response))
        val task = FetchIpClaimTask(deviceInfo, advertiser("a"), api, RecordingLogger(), IPProtocol.IPv6)

        val token = runBlocking { task.execute() }

        assertEquals("T6", token)
        assertEquals(IPProtocol.IPv6, api.lastProtocol)
    }

    @Test
    fun execute_success_withNullAdvertiserId_passesNullThrough() {
        val response = JSONObject().put("ip", "1.1.1.1").put("type", "v4").put("token", "tk")
        val api = RecordingAttributionApi(Result.success(response))
        val task = FetchIpClaimTask(deviceInfo, advertiser(null), api, RecordingLogger(), IPProtocol.IPv4)

        runBlocking { task.execute() }

        assertNull(api.lastAdvertiserId)
    }

    // ---------- success but parse fails ----------

    @Test
    fun execute_success_butResponseMissingFields_throwsJSONException() {
        val malformed = JSONObject().put("ip", "1.2.3.4") // missing "type", "token"
        val api = RecordingAttributionApi(Result.success(malformed))
        val task = FetchIpClaimTask(deviceInfo, advertiser("a"), api, RecordingLogger(), IPProtocol.IPv4)

        assertThrows(JSONException::class.java) { runBlocking { task.execute() } }
    }

    // ---------- failure paths ----------

    @Test
    fun execute_failureWithException_rethrows_andDoesNotLog_whenNonCritical() {
        val cause = RuntimeException("non-critical failure")
        val api = RecordingAttributionApi(Result.failure(cause))
        val logger = RecordingLogger()
        val task = FetchIpClaimTask(deviceInfo, advertiser("a"), api, logger, IPProtocol.IPv4)

        val thrown = assertThrows(RuntimeException::class.java) { runBlocking { task.execute() } }
        assertSame(cause, thrown)
        // No critical -> no error log; no success -> no debug/metric
        assertTrue(logger.errors.isEmpty())
        assertTrue(logger.metrics.isEmpty())
        assertTrue(logger.debugs.isEmpty())
    }

    @Test
    fun execute_failureWith401BadResponse_logsError_andRethrows() {
        val cause = BadResponseException("Unauthorized", 401)
        val api = RecordingAttributionApi(Result.failure(cause))
        val logger = RecordingLogger()
        val task = FetchIpClaimTask(deviceInfo, advertiser("a"), api, logger, IPProtocol.IPv4)

        val thrown = assertThrows(BadResponseException::class.java) { runBlocking { task.execute() } }
        assertSame(cause, thrown)
        val errorCall = logger.errorsWithException.single()
        assertEquals("Unauthorized", errorCall.message)
        assertSame(cause, errorCall.exception)
    }

    @Test
    fun execute_failureWith500BadResponse_isNotLoggedAsError() {
        val cause = BadResponseException("Server boom", 500)
        val api = RecordingAttributionApi(Result.failure(cause))
        val logger = RecordingLogger()
        val task = FetchIpClaimTask(deviceInfo, advertiser("a"), api, logger, IPProtocol.IPv4)

        assertThrows(BadResponseException::class.java) { runBlocking { task.execute() } }
        assertTrue(logger.errorsWithException.isEmpty())
    }

    // ---------- helpers ----------

    private fun advertiser(id: String?): AdvertiserIdProvider = AdvertiserIdProvider { ValueFuture(TestAdvertiserIdInfo(id)) }

    private class TestAdvertiserIdInfo(override val advertiserId: String?) : AdvertiserIdInfo {
        override val isLimitedAdTracking: Boolean = false
    }

    private class RecordingAttributionApi(
        private val result: Result<JSONObject>,
    ) : AttributionApi {
        var lastProtocol: IPProtocol? = null
        var lastAdvertiserId: String? = null
        val callCount = AtomicInteger(0)

        override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> =
            throw UnsupportedOperationException("not used")

        override suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject> {
            callCount.incrementAndGet()
            lastProtocol = protocol
            lastAdvertiserId = advertiserId
            return result
        }

        override suspend fun sendCustomUserId(
            body: DTOPublishCustomUserIdRequest,
            advertiserId: String?,
            uuid: String,
            installId: String,
        ): Result<Unit> = throw UnsupportedOperationException("not used")

        override suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit> =
            throw UnsupportedOperationException("not used")
    }

    private data class MetricCall(val metric: Metric, val value: Double, val dimensions: List<io.justtrack.log.LoggerFields>)
    private data class LogCall(val message: String, val fields: List<io.justtrack.log.LoggerFields>)
    private data class ErrorWithException(val message: String, val exception: Throwable)

    private class RecordingLogger : io.justtrack.log.Logger {
        val debugs = mutableListOf<LogCall>()
        val errors = mutableListOf<LogCall>()
        val errorsWithException = mutableListOf<ErrorWithException>()
        val metrics = mutableListOf<MetricCall>()

        override val fallback: io.justtrack.log.Logger get() = this

        override fun debug(message: String, vararg fields: io.justtrack.log.LoggerFields) {
            debugs += LogCall(message, fields.toList())
        }

        override fun info(message: String, vararg fields: io.justtrack.log.LoggerFields) = Unit
        override fun warn(message: String, vararg fields: io.justtrack.log.LoggerFields) = Unit
        override fun warn(message: String, exception: Throwable, vararg fields: io.justtrack.log.LoggerFields) = Unit

        override fun error(message: String, vararg fields: io.justtrack.log.LoggerFields) {
            errors += LogCall(message, fields.toList())
        }

        override fun error(message: String, exception: Throwable, vararg fields: io.justtrack.log.LoggerFields) {
            errorsWithException += ErrorWithException(message, exception)
        }

        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: io.justtrack.log.LoggerFields) {
            metrics += MetricCall(metric, value, dimensions.toList())
        }
    }
}
