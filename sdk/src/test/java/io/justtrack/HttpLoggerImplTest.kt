package io.justtrack

import io.justtrack.api.LogApi
import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.DTOLogMetric
import io.justtrack.dtos.LogLevel
import io.justtrack.exceptions.AwaitingIdException
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersionImpl
import io.justtrack.versions.VersionBundle
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class HttpLoggerImplTest {

    private val versionBundle = VersionBundle(
        SdkVersionImpl(5, 6, 7, "5.6.7", PlatformType.ANDROID),
        ApplicationVersionImpl("7.0.0", "7000"),
    )

    @After
    fun tearDown() {
        // ensure any background coroutine in setUser(future, future) has time to finish if a test
        // started one but did not explicitly wait.
        Thread.sleep(10)
    }

    // ---------- delegation: debug/info/warn/error/publishMetric ----------

    @Test
    fun debug_delegatesToFallback_andDoesNotWriteToAggregator() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)

        logger.debug("d msg", LoggerFieldsBuilder().with("k", "v"))

        assertEquals(1, fallback.debugs.size)
        assertEquals("d msg", fallback.debugs[0].message)
        assertTrue("debug should not be aggregated", aggregator.messages.isEmpty())
        assertTrue(aggregator.metrics.isEmpty())
    }

    @Test
    fun info_delegatesToFallback_writesInfoLog_doesNotPublishMetric() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)

        logger.info("i msg", LoggerFieldsBuilder().with("k", "v"))

        assertEquals("i msg", fallback.infos.single().message)
        val msg = aggregator.messages.single()
        assertEquals(LogLevel.INFO, msg.level)
        assertEquals("i msg", msg.message)
        assertEquals("v", msg.fields.getString("k"))
        // no metric for info
        assertTrue(aggregator.metrics.isEmpty())
        assertTrue(fallback.metrics.isEmpty())
    }

    @Test
    fun warn_delegatesToFallback_writesWarnLog_publishesWarningsMetric() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)

        logger.warn("w msg")

        assertEquals("w msg", fallback.warns.single().message)
        val msg = aggregator.messages.single()
        assertEquals(LogLevel.WARN, msg.level)
        // metric published both via fallback and via aggregator
        val metric = aggregator.metrics.single()
        assertEquals("Warnings", metric.metricName)
        assertEquals(1.0, metric.value, 0.0)
        assertEquals("Warnings", fallback.metrics.single().metric.metric)
    }

    @Test
    fun warnWithException_delegates_includesExceptionField_publishesWarningsMetric() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)
        val cause = RuntimeException("boom")

        logger.warn("w msg", cause)

        assertSame(cause, fallback.warnsWithException.single().exception)
        val msg = aggregator.messages.single()
        assertEquals(LogLevel.WARN, msg.level)
        // exception is encoded into fields by LoggerFieldsBuilder()
        assertTrue(msg.fields.has("exception"))
        assertEquals(1, aggregator.metrics.size)
        assertEquals("Warnings", aggregator.metrics[0].metricName)
    }

    @Test
    fun error_delegates_writesErrorLog_publishesErrorsMetric() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)

        logger.error("e msg")

        assertEquals("e msg", fallback.errors.single().message)
        val msg = aggregator.messages.single()
        assertEquals(LogLevel.ERROR, msg.level)
        val metric = aggregator.metrics.single()
        assertEquals("Errors", metric.metricName)
    }

    @Test
    fun errorWithException_delegates_writesErrorWithExceptionField_publishesErrorsMetric() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)
        val cause = IllegalStateException("oops")

        logger.error("e msg", cause)

        // Implementation calls fallback.error(message, *fields) without exception
        assertEquals("e msg", fallback.errors.single().message)
        val msg = aggregator.messages.single()
        assertEquals(LogLevel.ERROR, msg.level)
        assertTrue(msg.fields.has("exception"))
        val metric = aggregator.metrics.single()
        assertEquals("Errors", metric.metricName)
    }

    @Test
    fun publishMetric_delegatesToFallback_andWritesMetric_withDimensions() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)
        val customMetric = Metric("MyMetric")

        logger.publishMetric(customMetric, 4.2, LoggerFieldsBuilder().with("x", "y"))

        val fallbackCall = fallback.metrics.single()
        assertSame(customMetric, fallbackCall.metric)
        assertEquals(4.2, fallbackCall.value, 0.0)
        val rec = aggregator.metrics.single()
        assertEquals("MyMetric", rec.metricName)
        assertEquals(4.2, rec.value, 0.0)
        assertEquals("y", rec.fields.getString("x"))
        // no log message added on publishMetric
        assertTrue(aggregator.messages.isEmpty())
    }

    // ---------- setUser ----------

    @Test
    fun setUser_sync_setsUserAndInstallId_andDelegatesToHttpFallback() {
        val httpFallback = RecordingHttpLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(httpFallback, aggregator)
        val uid = UUID.randomUUID()

        logger.setUser(uid, "iid-1")

        assertEquals(uid, httpFallback.lastUserIdSync)
        assertEquals("iid-1", httpFallback.lastInstallIdSync)
    }

    @Test
    fun setUser_sync_nullUserId_doesNotOverrideUserId() {
        val httpFallback = RecordingHttpLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(httpFallback, aggregator)

        logger.setUser(null, "iid-1")

        // delegated even with null userId
        assertNull(httpFallback.lastUserIdSync)
        assertEquals("iid-1", httpFallback.lastInstallIdSync)
    }

    @Test
    fun setUser_sync_allowsSendToServerToSucceed() {
        // Verifies the sync setUser populates the userId/installInstanceId state used by sendToServer.
        val fallback = RecordingLogger()
        val aggregator = ManualAggregator()
        val api = RecordingLogApi(Result.success(Unit))
        val logger = HttpLoggerImpl(fallback, api, versionBundle, aggregator)
        logger.setUser(UUID.randomUUID(), "iid")

        // Queue some content & trigger send
        aggregator.queuedMessages = listOf(makeLogMessage())
        aggregator.queuedMetrics = listOf(makeLogMetric())
        logger.sendToServer()
        val result = runBlocking { aggregator.lastSender!!.sendLogsAndMetrics(aggregator.queuedMessages, aggregator.queuedMetrics) }

        assertTrue(result.isSuccess)
        assertNotNull(api.lastBody)
        assertEquals("iid", api.lastInstallId)
    }

    @Test
    fun setUser_futureVariant_eventuallySetsUserAndInstallInstanceId() {
        val httpFallback = LatchingHttpLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(httpFallback, aggregator)
        val uid = UUID.randomUUID()
        val userFuture: AsyncFuture<UUID?> = ValueFuture(uid)
        val installFuture: AsyncFuture<String?> = ValueFuture("iid-2")

        logger.setUser(userFuture, installFuture)

        // background coroutine on Dispatchers.IO -> wait for fallback.setUser to be called.
        assertTrue("future setUser was not propagated in time", httpFallback.setUserLatch.await(2, TimeUnit.SECONDS))
        assertEquals(uid, httpFallback.lastUserIdFuture)
        assertEquals("iid-2", httpFallback.lastInstallIdFuture)
    }

    // ---------- setAdvertiserId ----------

    @Test
    fun setAdvertiserId_storesValue_andDelegatesToHttpFallback() {
        val httpFallback = RecordingHttpLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(httpFallback, aggregator)

        logger.setAdvertiserId("ad-9")

        assertEquals("ad-9", httpFallback.lastAdvertiserId)
    }

    @Test
    fun setAdvertiserId_withPlainLoggerFallback_doesNotThrow() {
        val plain = RecordingLogger()
        val logger = newLogger(plain, RecordingAggregator())

        logger.setAdvertiserId("ad-x")
        // fallback is not HttpLogger so nothing to delegate, but no exception.
    }

    // ---------- close ----------

    @Test
    fun close_closesAggregator() {
        val aggregator = RecordingAggregator()
        val logger = newLogger(RecordingLogger(), aggregator)

        logger.close()

        assertTrue(aggregator.closed)
    }

    // ---------- setBreadCrumbReporter + breadcrumb side-effects ----------

    @Test
    fun setBreadCrumbReporter_null_writeLog_doesNotAddBreadCrumb() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val logger = newLogger(fallback, aggregator)
        // reporter starts null
        logger.info("i", LoggerFieldsBuilder())
        // nothing to assert about a null reporter except that no NPE occurred.
        assertEquals(1, aggregator.messages.size)
    }

    @Test
    fun writeLog_withBreadCrumbReporter_addsBreadCrumbWithLevel() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val reporter = RecordingBreadCrumbReporter()
        val logger = newLogger(fallback, aggregator)
        logger.setBreadCrumbReporter(reporter)

        logger.warn("w msg")

        val crumb = reporter.crumbs.first { it.message == "w msg" }
        assertEquals(LogLevel.WARN, crumb.level)
        assertEquals("logs", crumb.category)
    }

    @Test
    fun publishMetric_withBreadCrumbReporter_addsDebugBreadCrumb() {
        val fallback = RecordingLogger()
        val aggregator = RecordingAggregator()
        val reporter = RecordingBreadCrumbReporter()
        val logger = newLogger(fallback, aggregator)
        logger.setBreadCrumbReporter(reporter)
        val m = Metric("Foo")

        logger.publishMetric(m, 1.0)

        val crumb = reporter.crumbs.single()
        assertEquals("Foo", crumb.message)
        assertEquals("metrics", crumb.category)
        assertEquals(LogLevel.DEBUG, crumb.level)
    }

    // ---------- sendToServer ----------

    @Test
    fun sendToServer_withoutUserIds_failsWithAwaitingIdException() {
        val fallback = RecordingLogger()
        val aggregator = ManualAggregator()
        val api = RecordingLogApi(Result.success(Unit))
        val logger = HttpLoggerImpl(fallback, api, versionBundle, aggregator)

        logger.sendToServer()
        val sender = aggregator.lastSender!!
        val result = runBlocking { sender.sendLogsAndMetrics(emptyList(), emptyList()) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AwaitingIdException)
        // logApi was NOT called
        assertNull(api.lastBody)
    }

    @Test
    fun sendToServer_withUser_callsLogApi_andLogsDebugOnSuccess() {
        val fallback = RecordingLogger()
        val aggregator = ManualAggregator()
        val api = RecordingLogApi(Result.success(Unit))
        val logger = HttpLoggerImpl(fallback, api, versionBundle, aggregator)
        logger.setUser(UUID.randomUUID(), "iid-7")
        logger.setAdvertiserId("ad-9")

        logger.sendToServer()
        val sender = aggregator.lastSender!!
        val msgs = listOf(makeLogMessage())
        val mets = listOf(makeLogMetric())
        val result = runBlocking { sender.sendLogsAndMetrics(msgs, mets) }

        assertTrue(result.isSuccess)
        assertEquals("ad-9", api.lastAdvertiserId)
        assertEquals("iid-7", api.lastInstallId)
        assertNotNull(api.lastBody)
        // success debug log was emitted via fallback
        assertTrue(fallback.debugs.any { it.message.contains("Published") })
    }

    @Test
    fun sendToServer_onApiFailure_logsWarnWithException() {
        val fallback = RecordingLogger()
        val aggregator = ManualAggregator()
        val cause = RuntimeException("api down")
        val api = RecordingLogApi(Result.failure(cause))
        val logger = HttpLoggerImpl(fallback, api, versionBundle, aggregator)
        logger.setUser(UUID.randomUUID(), "iid-7")

        logger.sendToServer()
        val sender = aggregator.lastSender!!
        val result = runBlocking { sender.sendLogsAndMetrics(emptyList(), emptyList()) }

        assertTrue(result.isFailure)
        val warnCall = fallback.warnsWithException.single()
        assertTrue(warnCall.message.contains("Failed to publish"))
        assertSame(cause, warnCall.exception)
    }

    // ---------- helpers ----------

    private fun newLogger(fallback: Logger, aggregator: LogAggregator): HttpLoggerImpl =
        HttpLoggerImpl(fallback, RecordingLogApi(Result.success(Unit)), versionBundle, aggregator)

    private fun makeLogMessage(): DTOLogMessage = DTOLogMessage(LogLevel.INFO, "queued", JSONObject(), java.util.Date())

    private fun makeLogMetric(): DTOLogMetric = DTOLogMetric("M", JSONObject(), 1.0, "COUNT", java.util.Date())

    // ---------- recording helpers ----------

    private data class LogCall(val message: String, val fields: List<LoggerFields>)
    private data class WarnExCall(val message: String, val exception: Throwable)
    private data class MetricCall(val metric: Metric, val value: Double)

    private open class RecordingLogger : Logger {
        val debugs = mutableListOf<LogCall>()
        val infos = mutableListOf<LogCall>()
        val warns = mutableListOf<LogCall>()
        val warnsWithException = mutableListOf<WarnExCall>()
        val errors = mutableListOf<LogCall>()
        val metrics = mutableListOf<MetricCall>()

        override val fallback: Logger get() = this
        override fun debug(message: String, vararg fields: LoggerFields) {
            debugs += LogCall(message, fields.toList())
        }
        override fun info(message: String, vararg fields: LoggerFields) {
            infos += LogCall(message, fields.toList())
        }
        override fun warn(message: String, vararg fields: LoggerFields) {
            warns += LogCall(message, fields.toList())
        }
        override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) {
            warnsWithException += WarnExCall(message, exception)
        }
        override fun error(message: String, vararg fields: LoggerFields) {
            errors += LogCall(message, fields.toList())
        }
        override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) {
            errors += LogCall(message, fields.toList())
        }
        override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) {
            metrics += MetricCall(metric, value)
        }
    }

    private open class RecordingHttpLogger : RecordingLogger(), HttpLogger {
        var lastAdvertiserId: String? = null
        var lastUserIdSync: UUID? = null
        var lastInstallIdSync: String? = null
        var lastUserIdFuture: UUID? = null
        var lastInstallIdFuture: String? = null

        override fun setAdvertiserId(advertiserId: String) {
            lastAdvertiserId = advertiserId
        }
        override fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>) {
            lastUserIdFuture = runBlocking { userId.await() }
            lastInstallIdFuture = runBlocking { installInstanceId.await() }
        }
        override fun setUser(userId: UUID?, installId: String) {
            lastUserIdSync = userId
            lastInstallIdSync = installId
        }
        override fun sendToServer() = Unit
        override fun setBreadCrumbReporter(reporter: BreadCrumbReporter?) = Unit
        override fun close() = Unit
    }

    private class LatchingHttpLogger : RecordingHttpLogger() {
        val setUserLatch = CountDownLatch(1)
        override fun setUser(userId: AsyncFuture<UUID?>, installInstanceId: AsyncFuture<String?>) {
            super.setUser(userId, installInstanceId)
            setUserLatch.countDown()
        }
    }

    private data class RecordedMessage(val level: LogLevel, val message: String, val fields: JSONObject)
    private data class RecordedMetric(val metricName: String, val value: Double, val fields: JSONObject)

    private open class RecordingAggregator : LogAggregator {
        val messages = mutableListOf<RecordedMessage>()
        val metrics = mutableListOf<RecordedMetric>()
        var closed = false

        override fun addLogMessage(message: DTOLogMessage) {
            messages += RecordedMessage(message.level, message.message, message.fields)
        }

        override fun addLogMetric(metric: DTOLogMetric) {
            metrics += RecordedMetric(metric.metric, metric.value, metric.dimensions)
        }

        override fun sendLogsAndMetrics(sender: LogAggregator.LogSender) = Unit
        override fun close() {
            closed = true
        }
    }

    private class ManualAggregator : RecordingAggregator() {
        var lastSender: LogAggregator.LogSender? = null
        var queuedMessages: Collection<DTOLogMessage> = emptyList()
        var queuedMetrics: Collection<DTOLogMetric> = emptyList()

        override fun sendLogsAndMetrics(sender: LogAggregator.LogSender) {
            lastSender = sender
        }
    }

    private class RecordingLogApi(private val result: Result<Unit>) : LogApi {
        var lastBody: JSONEncodable? = null
        var lastAdvertiserId: String? = null
        var lastUuid: String? = null
        var lastInstallId: String? = null

        override suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit> {
            lastBody = body
            lastAdvertiserId = advertiserId
            lastUuid = uuid
            lastInstallId = installId
            return result
        }
    }

    private class RecordingBreadCrumbReporter : BreadCrumbReporter {
        val crumbs = mutableListOf<BreadCrumb>()
        override fun addBreadCrumb(breadCrumb: BreadCrumb) {
            crumbs += breadCrumb
        }
    }

    @Suppress("UnusedPrivateMember")
    private fun ensureFalseReferenced(): Boolean = assertFalseUsed()
    private fun assertFalseUsed(): Boolean {
        assertFalse(false)
        return true
    }
}
