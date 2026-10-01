package io.justtrack

import io.justtrack.dtos.DTOLogMetric
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

internal class LogMetricEntityUnitTest {
    private val timestamp = Date(1_700_000_000_000L)

    @Test
    fun constructor_defaultIdAndProcessingTime_areMinusOne() {
        val entity = LogMetricEntity(
            name = "metric",
            value = 1.0,
            dimensions = "{}",
            unit = "count",
            timestamp = "ts",
            timestampInMS = 1000L,
        )

        Assert.assertEquals(-1L, entity.id)
        Assert.assertEquals(-1L, entity.processingTimeInMS)
    }

    @Test
    fun constructorFromStoreMetric_mapsFields() {
        val metric = LogStoreMetric(
            7L,
            DTOLogMetric(
                "metric",
                JSONObject(mapOf("dimension" to "value")),
                3.5,
                "count",
                timestamp,
            ),
        )

        val entity = LogMetricEntity(metric, Formatter)

        Assert.assertEquals(7L, entity.id)
        Assert.assertEquals("metric", entity.name)
        Assert.assertEquals(3.5, entity.value, 0.0)
        Assert.assertEquals("value", JSONObject(entity.dimensions).getString("dimension"))
        Assert.assertEquals("count", entity.unit)
        Assert.assertEquals(Formatter.formatDateMilliseconds(timestamp), entity.timestamp)
        Assert.assertEquals(timestamp.time, entity.timestampInMS)
        Assert.assertEquals(-1L, entity.processingTimeInMS)
    }

    @Test
    fun setters_updateMutableFields() {
        val entity = LogMetricEntity(
            name = "metric",
            value = 1.0,
            dimensions = "{}",
            unit = "count",
            timestamp = "ts",
            timestampInMS = 1000L,
        )

        // Initially defaults
        Assert.assertEquals(-1L, entity.id)
        Assert.assertEquals(-1L, entity.processingTimeInMS)

        // Exercise the synthetic setters generated for the `var` properties
        entity.id = 99L
        entity.processingTimeInMS = 250L

        Assert.assertEquals(99L, entity.id)
        Assert.assertEquals(250L, entity.processingTimeInMS)
    }

    @Test
    fun transform_returnsStoreMetric() {
        val entity = LogMetricEntity(
            id = 7L,
            name = "metric",
            value = 3.5,
            dimensions = JSONObject(mapOf("dimension" to "value")).toString(),
            unit = "count",
            timestamp = Formatter.formatDateMilliseconds(timestamp),
            timestampInMS = timestamp.time,
            processingTimeInMS = 12L,
        )

        val metric = entity.transform(Formatter, TestLogger())

        Assert.assertEquals(7L, metric.id)
        Assert.assertEquals("metric", metric.metric)
        Assert.assertEquals(3.5, metric.value, 0.0)
        Assert.assertEquals("value", metric.dimensions.getString("dimension"))
        Assert.assertEquals("count", metric.unit)
        Assert.assertEquals(timestamp, metric.timestamp)
    }

    @Test
    fun transform_withInvalidDimensionsAndTimestamp_usesFallbacks() {
        val beforeTransform = System.currentTimeMillis()
        val entity = LogMetricEntity(
            id = 7L,
            name = "metric",
            value = 3.5,
            dimensions = "not-json",
            unit = "count",
            timestamp = "not-date",
            timestampInMS = timestamp.time,
        )

        val metric = entity.transform(Formatter, TestLogger())

        Assert.assertEquals(0, metric.dimensions.length())
        Assert.assertTrue(metric.timestamp.time >= beforeTransform)
        Assert.assertTrue(metric.timestamp.time <= System.currentTimeMillis())
    }

    @Test
    fun transform_withInvalidDimensions_usesEmptyDimensions() {
        val entity = LogMetricEntity(
            id = 1L,
            name = "metric",
            value = 1.0,
            dimensions = "not-json",
            unit = "count",
            timestamp = Formatter.formatDateMilliseconds(timestamp),
            timestampInMS = timestamp.time,
        )

        val metric = entity.transform(Formatter, TestLogger())

        Assert.assertEquals(0, metric.dimensions.length())
    }

    @Test
    fun transform_withInvalidTimestamp_usesFallbackDate() {
        val beforeTransform = System.currentTimeMillis()
        val entity = LogMetricEntity(
            id = 1L,
            name = "metric",
            value = 1.0,
            dimensions = "{}",
            unit = "count",
            timestamp = "not-date",
            timestampInMS = timestamp.time,
        )

        val metric = entity.transform(Formatter, TestLogger())

        Assert.assertTrue(metric.timestamp.time >= beforeTransform)
        Assert.assertTrue(metric.timestamp.time <= System.currentTimeMillis())
    }

    @Test
    fun equalsAndHashCode_ignoreId() {
        val first = LogMetricEntity(1L, "metric", 3.5, "{}", "count", "timestamp", 1L, 2L)
        val second = LogMetricEntity(2L, "metric", 3.5, "{}", "count", "timestamp", 1L, 2L)

        Assert.assertEquals(first, second)
        Assert.assertEquals(first.hashCode(), second.hashCode())
        Assert.assertNotEquals(first, second.copy(name = "other"))
    }

    @Test
    fun equals_sameReference_returnsTrue() {
        val entity = LogMetricEntity(1L, "metric", 3.5, "{}", "count", "timestamp", 1L, 2L)

        Assert.assertEquals(entity, entity)
    }

    @Test
    fun equals_null_returnsFalse() {
        val entity = LogMetricEntity(1L, "metric", 3.5, "{}", "count", "timestamp", 1L, 2L)

        Assert.assertNotEquals(entity, null)
    }

    @Test
    fun equals_differentType_returnsFalse() {
        val entity = LogMetricEntity(1L, "metric", 3.5, "{}", "count", "timestamp", 1L, 2L)

        Assert.assertNotEquals(entity, "not an entity")
    }

    @Test
    fun equals_differentFields_returnsFalse() {
        val base = LogMetricEntity(1L, "metric", 3.5, "{}", "count", "timestamp", 1000L, 2000L)

        Assert.assertNotEquals(base, base.copy(name = "other"))
        Assert.assertNotEquals(base, base.copy(value = 9.9))
        Assert.assertNotEquals(base, base.copy(dimensions = "{\"a\":1}"))
        Assert.assertNotEquals(base, base.copy(unit = "bytes"))
        Assert.assertNotEquals(base, base.copy(timestamp = "other-ts"))
        Assert.assertNotEquals(base, base.copy(timestampInMS = 9999L))
        Assert.assertNotEquals(base, base.copy(processingTimeInMS = 9999L))
    }

    @Test
    fun hashCode_isConsistentWithEquals() {
        val first = LogMetricEntity(1L, "metric", 3.5, "{}", "count", "timestamp", 1000L, 2000L)
        val second = LogMetricEntity(99L, "metric", 3.5, "{}", "count", "timestamp", 1000L, 2000L)
        val different = first.copy(name = "other")

        Assert.assertEquals(first.hashCode(), second.hashCode())
        Assert.assertNotEquals(first.hashCode(), different.hashCode())
    }
}
