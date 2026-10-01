package io.justtrack

import android.util.Log
import io.justtrack.events.MetricUnit
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class LoggerImplTest {

    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    @Test
    fun debug_writesToLogcatWhenEnabled() {
        LoggerImpl(isLogEnabled = true).debug("hello")

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        assertEquals(Log.DEBUG, logs[0].type)
        assertEquals("hello", logs[0].msg)
    }

    @Test
    fun debug_doesNothingWhenDisabled() {
        LoggerImpl(isLogEnabled = false).debug("hello")

        assertTrue(ShadowLog.getLogsForTag(LoggerImpl.TAG).isEmpty())
    }

    @Test
    fun info_writesToLogcatWhenEnabled() {
        LoggerImpl(isLogEnabled = true).info("hello")

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        assertEquals(Log.INFO, logs[0].type)
        assertEquals("hello", logs[0].msg)
    }

    @Test
    fun info_doesNothingWhenDisabled() {
        LoggerImpl(isLogEnabled = false).info("hello")

        assertTrue(ShadowLog.getLogsForTag(LoggerImpl.TAG).isEmpty())
    }

    @Test
    fun warn_writesToLogcatWhenEnabled() {
        LoggerImpl(isLogEnabled = true).warn("hello")

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        assertEquals(Log.WARN, logs[0].type)
        assertEquals("hello", logs[0].msg)
    }

    @Test
    fun warn_doesNothingWhenDisabled() {
        LoggerImpl(isLogEnabled = false).warn("hello")

        assertTrue(ShadowLog.getLogsForTag(LoggerImpl.TAG).isEmpty())
    }

    @Test
    fun warnWithException_writesToLogcatWhenEnabled() {
        val exception = IllegalStateException("boom")
        LoggerImpl(isLogEnabled = true).warn("hello", exception)

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        assertEquals(Log.WARN, logs[0].type)
        assertEquals("hello", logs[0].msg)
        assertSame(exception, logs[0].throwable)
    }

    @Test
    fun warnWithException_doesNothingWhenDisabled() {
        LoggerImpl(isLogEnabled = false).warn("hello", IllegalStateException("boom"))

        assertTrue(ShadowLog.getLogsForTag(LoggerImpl.TAG).isEmpty())
    }

    @Test
    fun error_alwaysWritesEvenWhenDisabled() {
        LoggerImpl(isLogEnabled = false).error("hello")

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        assertEquals(Log.ERROR, logs[0].type)
        assertEquals("hello", logs[0].msg)
    }

    @Test
    fun errorWithException_alwaysWritesEvenWhenDisabled() {
        val exception = IllegalStateException("boom")
        LoggerImpl(isLogEnabled = false).error("hello", exception)

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        assertEquals(Log.ERROR, logs[0].type)
        assertEquals("hello", logs[0].msg)
        assertSame(exception, logs[0].throwable)
    }

    @Test
    fun publishMetric_writesInfoLogWithMetricFieldsAndDimensions() {
        val metric = Metric(metric = "downloads", unit = MetricUnit.COUNT)
        val dimension = LoggerFieldsBuilder().with("source", "play_store")

        LoggerImpl(isLogEnabled = true).publishMetric(metric, 1.5, dimension)

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        assertEquals(Log.INFO, logs[0].type)
        val msg = logs[0].msg
        assertTrue("missing metricName: $msg", msg.contains("metricName = downloads"))
        assertTrue("missing metricValue: $msg", msg.contains("metricValue = 1.5"))
        assertTrue("missing metricUnit: $msg", msg.contains("metricUnit = ${MetricUnit.COUNT.unit}"))
        assertTrue("missing dimension: $msg", msg.contains("source = play_store"))
    }

    @Test
    fun publishMetric_withoutDimensionsStillWritesMetricFields() {
        val metric = Metric(metric = "starts", unit = MetricUnit.SECONDS)

        LoggerImpl(isLogEnabled = true).publishMetric(metric, 2.0)

        val logs = ShadowLog.getLogsForTag(LoggerImpl.TAG)
        assertEquals(1, logs.size)
        val msg = logs[0].msg
        assertTrue("missing metricName: $msg", msg.contains("metricName = starts"))
        assertTrue("missing metricValue: $msg", msg.contains("metricValue = 2.0"))
    }

    @Test
    fun fallback_returnsSelf() {
        val logger = LoggerImpl(isLogEnabled = false)

        assertSame(logger, logger.fallback)
    }

    @Test
    fun isLogEnabled_exposesConstructorValue() {
        assertEquals(true, LoggerImpl(isLogEnabled = true).isLogEnabled)
        assertEquals(false, LoggerImpl(isLogEnabled = false).isLogEnabled)
    }

    @Test
    fun defaultConstructor_disablesLogging() {
        // Exercises the default-parameter synthetic constructor (isLogEnabled = false).
        assertEquals(false, LoggerImpl().isLogEnabled)
    }

    @Test
    fun encodeMessage_returnsRawMessageWhenNoFields() {
        val logger = TestableLoggerImpl()

        assertEquals("hello", logger.exposeEncodeMessage("hello"))
    }

    @Test
    fun encodeMessage_appendsFieldKeyValuePairsForEachLoggerFields() {
        val logger = TestableLoggerImpl()
        val fieldsA = LoggerFieldsBuilder().with("k1", "v1").with("k2", "v2")
        val fieldsB = LoggerFieldsBuilder().with("k3", "v3")

        val encoded = logger.exposeEncodeMessage("hello", fieldsA, fieldsB)

        assertEquals("hello, k1 = v1, k2 = v2, k3 = v3", encoded)
    }

    @Test
    fun encodeMessage_skipsNullLoggerFieldsEntries() {
        val logger = TestableLoggerImpl()
        val fields = LoggerFieldsBuilder().with("k", "v")

        val encoded = logger.exposeEncodeMessage("msg", null, fields, null)

        assertEquals("msg, k = v", encoded)
    }

    @Test
    fun companionTagIsPresent() {
        assertNotNull(LoggerImpl.TAG)
        assertEquals("JustTrackSdk", LoggerImpl.TAG)
    }

    private class TestableLoggerImpl : LoggerImpl(isLogEnabled = false) {
        fun exposeEncodeMessage(message: String, vararg fields: LoggerFields?): String = encodeMessage(message, *fields)
    }
}
