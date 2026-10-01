package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DTOLogMessageTest {
    private val timestamp = Date(1_592_228_819_000L)

    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val fields = JSONObject().apply { put("key", "value") }
        val dto = DTOLogMessage(
            level = LogLevel.INFO,
            message = "hello world",
            fields = fields,
            timestamp = timestamp,
        )

        Assert.assertEquals(LogLevel.INFO, dto.level)
        Assert.assertEquals("hello world", dto.message)
        Assert.assertEquals(fields, dto.fields)
        Assert.assertEquals(timestamp, dto.timestamp)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorParsesFields() {
        val input = JSONObject().apply {
            put("level", "warn")
            put("message", "a warning")
            put("fields", JSONObject().apply { put("foo", "bar") })
            put("timestamp", Formatter.formatDateMilliseconds(timestamp))
        }

        val dto = DTOLogMessage(input, Formatter)

        Assert.assertEquals(LogLevel.WARN, dto.level)
        Assert.assertEquals("a warning", dto.message)
        Assert.assertEquals("bar", dto.fields.getString("foo"))
        Assert.assertEquals(timestamp, dto.timestamp)
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorIsCaseInsensitiveForLevel() {
        val input = JSONObject().apply {
            put("level", "ERROR")
            put("message", "oops")
            put("fields", JSONObject())
            put("timestamp", Formatter.formatDateMilliseconds(timestamp))
        }

        val dto = DTOLogMessage(input, Formatter)

        Assert.assertEquals(LogLevel.ERROR, dto.level)
    }

    @Test
    @Throws(JSONException::class)
    fun copyConstructorCopiesAllFields() {
        val fields = JSONObject().apply { put("x", "y") }
        val original = DTOLogMessage(LogLevel.DEBUG, "msg", fields, timestamp)

        val copy = object : DTOLogMessage(original) {}

        Assert.assertEquals(original.level, copy.level)
        Assert.assertEquals(original.message, copy.message)
        Assert.assertEquals(original.fields, copy.fields)
        Assert.assertEquals(original.timestamp, copy.timestamp)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val fields = JSONObject().apply { put("env", "prod") }
        val dto = DTOLogMessage(
            level = LogLevel.ERROR,
            message = "error occurred",
            fields = fields,
            timestamp = timestamp,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals("error", json.getString("level"))
        Assert.assertEquals("error occurred", json.getString("message"))
        Assert.assertEquals("prod", json.getJSONObject("fields").getString("env"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(timestamp), json.getString("timestamp"))
    }

    @Test
    @Throws(JSONException::class)
    fun jsonConstructorRoundTripsViaToJson() {
        val dto = DTOLogMessage(LogLevel.INFO, "roundtrip", JSONObject().apply { put("a", "b") }, timestamp)
        val json = dto.toJSON(Formatter)
        val roundTripped = DTOLogMessage(json, Formatter)

        Assert.assertEquals(dto.level, roundTripped.level)
        Assert.assertEquals(dto.message, roundTripped.message)
        Assert.assertEquals(dto.timestamp, roundTripped.timestamp)
    }
}
