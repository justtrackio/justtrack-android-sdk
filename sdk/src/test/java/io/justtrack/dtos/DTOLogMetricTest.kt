package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DTOLogMetricTest {
    private val timestamp = Date(1_592_228_819_000L)

    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val dimensions = JSONObject().apply { put("region", "eu") }
        val dto = DTOLogMetric(
            metric = "latency",
            dimensions = dimensions,
            value = 123.45,
            unit = "ms",
            timestamp = timestamp,
        )

        Assert.assertEquals("latency", dto.metric)
        Assert.assertEquals(dimensions, dto.dimensions)
        Assert.assertEquals(123.45, dto.value, 0.0)
        Assert.assertEquals("ms", dto.unit)
        Assert.assertEquals(timestamp, dto.timestamp)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesFields() {
        val input = JSONObject().apply {
            put("metric", "requests")
            put("dimensions", JSONObject().apply { put("host", "server1") })
            put("value", 99.9)
            put("unit", "count")
            put("timestamp", Formatter.formatDateMilliseconds(timestamp))
        }

        val dto = DTOLogMetric(input, Formatter)

        Assert.assertEquals("requests", dto.metric)
        Assert.assertEquals("server1", dto.dimensions.getString("host"))
        Assert.assertEquals(99.9, dto.value, 0.0)
        Assert.assertEquals("count", dto.unit)
        Assert.assertEquals(timestamp, dto.timestamp)
    }

    @Test
    @Throws(JSONException::class)
    fun copyConstructorCopiesAllFields() {
        val dimensions = JSONObject().apply { put("k", "v") }
        val original = DTOLogMetric("m", dimensions, 1.0, "s", timestamp)

        val copy = object : DTOLogMetric(original) {}

        Assert.assertEquals(original.metric, copy.metric)
        Assert.assertEquals(original.dimensions, copy.dimensions)
        Assert.assertEquals(original.value, copy.value, 0.0)
        Assert.assertEquals(original.unit, copy.unit)
        Assert.assertEquals(original.timestamp, copy.timestamp)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dimensions = JSONObject().apply { put("dc", "us-east") }
        val dto = DTOLogMetric(
            metric = "cpu_usage",
            dimensions = dimensions,
            value = 0.75,
            unit = "percent",
            timestamp = timestamp,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("cpu_usage", json.getString("metric"))
        Assert.assertEquals("us-east", json.getJSONObject("dimensions").getString("dc"))
        Assert.assertEquals(0.75, json.getDouble("value"), 0.0)
        Assert.assertEquals("percent", json.getString("unit"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(timestamp), json.getString("timestamp"))
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorRoundTripsViaToJson() {
        val dto = DTOLogMetric("m", JSONObject().apply { put("k", "v") }, 3.14, "sec", timestamp)
        val json = dto.toJSON(Formatter)
        val roundTripped = DTOLogMetric(json, Formatter)

        Assert.assertEquals(dto.metric, roundTripped.metric)
        Assert.assertEquals(dto.value, roundTripped.value, 0.0)
        Assert.assertEquals(dto.unit, roundTripped.unit)
        Assert.assertEquals(dto.timestamp, roundTripped.timestamp)
    }
}
