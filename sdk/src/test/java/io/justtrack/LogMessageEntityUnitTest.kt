package io.justtrack

import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.LogLevel
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

internal class LogMessageEntityUnitTest {
    private val timestamp = Date(1_700_000_000_000L)

    @Test
    fun constructor_defaultIdAndProcessingTime_areMinusOne() {
        val entity = LogMessageEntity(
            level = "DEBUG",
            message = "message",
            fields = "{}",
            timestamp = "ts",
            timestampInMS = 1000L,
        )

        Assert.assertEquals(-1L, entity.id)
        Assert.assertEquals(-1L, entity.processingTimeInMS)
    }

    @Test
    fun constructor_defaultIdOnly_isMinusOne() {
        val entity = LogMessageEntity(
            level = "DEBUG",
            message = "message",
            fields = "{}",
            timestamp = "ts",
            timestampInMS = 1000L,
            processingTimeInMS = 500L,
        )

        Assert.assertEquals(-1L, entity.id)
        Assert.assertEquals(500L, entity.processingTimeInMS)
    }

    @Test
    fun constructor_defaultProcessingTimeOnly_isMinusOne() {
        val entity = LogMessageEntity(
            id = 42L,
            level = "DEBUG",
            message = "message",
            fields = "{}",
            timestamp = "ts",
            timestampInMS = 1000L,
        )

        Assert.assertEquals(42L, entity.id)
        Assert.assertEquals(-1L, entity.processingTimeInMS)
    }

    @Test
    fun constructorFromStoreMessage_mapsFields() {
        val message = LogStoreMessage(
            7L,
            DTOLogMessage(
                LogLevel.INFO,
                "message",
                JSONObject(mapOf("field" to "value")),
                timestamp,
            ),
        )

        val entity = LogMessageEntity(message, Formatter)

        Assert.assertEquals(7L, entity.id)
        Assert.assertEquals("INFO", entity.level)
        Assert.assertEquals("message", entity.message)
        Assert.assertEquals("value", JSONObject(entity.fields).getString("field"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(timestamp), entity.timestamp)
        Assert.assertEquals(timestamp.time, entity.timestampInMS)
        Assert.assertEquals(-1L, entity.processingTimeInMS)
    }

    @Test
    fun setters_updateMutableFields() {
        val entity = LogMessageEntity(
            level = "DEBUG",
            message = "message",
            fields = "{}",
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
    fun transform_returnsStoreMessage() {
        val entity = LogMessageEntity(
            id = 7L,
            level = "WARN",
            message = "message",
            fields = JSONObject(mapOf("field" to "value")).toString(),
            timestamp = Formatter.formatDateMilliseconds(timestamp),
            timestampInMS = timestamp.time,
            processingTimeInMS = 12L,
        )

        val message = entity.transform(Formatter, TestLogger())

        Assert.assertEquals(7L, message.id)
        Assert.assertEquals(LogLevel.WARN, message.level)
        Assert.assertEquals("message", message.message)
        Assert.assertEquals("value", message.fields.getString("field"))
        Assert.assertEquals(timestamp, message.timestamp)
    }

    @Test
    fun transform_withInvalidFieldsAndTimestamp_usesFallbacks() {
        val beforeTransform = System.currentTimeMillis()
        val entity = LogMessageEntity(
            id = 7L,
            level = "UNKNOWN",
            message = "message",
            fields = "not-json",
            timestamp = "not-date",
            timestampInMS = timestamp.time,
        )

        val message = entity.transform(Formatter, TestLogger())

        Assert.assertEquals(LogLevel.DEBUG, message.level)
        Assert.assertEquals(0, message.fields.length())
        Assert.assertTrue(message.timestamp.time >= beforeTransform)
        Assert.assertTrue(message.timestamp.time <= System.currentTimeMillis())
    }

    @Test
    fun transform_withInvalidFields_usesEmptyFields() {
        val entity = LogMessageEntity(
            id = 1L,
            level = "INFO",
            message = "message",
            fields = "not-json",
            timestamp = Formatter.formatDateMilliseconds(timestamp),
            timestampInMS = timestamp.time,
        )

        val message = entity.transform(Formatter, TestLogger())

        Assert.assertEquals(0, message.fields.length())
    }

    @Test
    fun transform_withInvalidTimestamp_usesFallbackDate() {
        val beforeTransform = System.currentTimeMillis()
        val entity = LogMessageEntity(
            id = 1L,
            level = "INFO",
            message = "message",
            fields = "{}",
            timestamp = "not-date",
            timestampInMS = timestamp.time,
        )

        val message = entity.transform(Formatter, TestLogger())

        Assert.assertTrue(message.timestamp.time >= beforeTransform)
        Assert.assertTrue(message.timestamp.time <= System.currentTimeMillis())
    }

    @Test
    fun transform_withUnknownLogLevel_fallsBackToDebug() {
        val entity = LogMessageEntity(
            id = 1L,
            level = "NOT_A_LEVEL",
            message = "message",
            fields = "{}",
            timestamp = Formatter.formatDateMilliseconds(timestamp),
            timestampInMS = timestamp.time,
        )

        val message = entity.transform(Formatter, TestLogger())

        Assert.assertEquals(io.justtrack.dtos.LogLevel.DEBUG, message.level)
    }

    @Test
    fun equalsAndHashCode_ignoreId() {
        val first = LogMessageEntity(1L, "DEBUG", "message", "{}", "timestamp", 1L, 2L)
        val second = LogMessageEntity(2L, "DEBUG", "message", "{}", "timestamp", 1L, 2L)

        Assert.assertEquals(first, second)
        Assert.assertEquals(first.hashCode(), second.hashCode())
        Assert.assertNotEquals(first, second.copy(message = "other"))
    }

    @Test
    fun equals_sameReference_returnsTrue() {
        val entity = LogMessageEntity(1L, "DEBUG", "message", "{}", "timestamp", 1L, 2L)

        Assert.assertEquals(entity, entity)
    }

    @Test
    fun equals_null_returnsFalse() {
        val entity = LogMessageEntity(1L, "DEBUG", "message", "{}", "timestamp", 1L, 2L)

        Assert.assertNotEquals(entity, null)
    }

    @Test
    fun equals_differentType_returnsFalse() {
        val entity = LogMessageEntity(1L, "DEBUG", "message", "{}", "timestamp", 1L, 2L)

        Assert.assertNotEquals(entity, "not an entity")
    }

    @Test
    fun equals_differentFields_returnsFalse() {
        val base = LogMessageEntity(1L, "DEBUG", "message", "{}", "timestamp", 1000L, 2000L)

        Assert.assertNotEquals(base, base.copy(level = "INFO"))
        Assert.assertNotEquals(base, base.copy(message = "other"))
        Assert.assertNotEquals(base, base.copy(fields = "{\"a\":1}"))
        Assert.assertNotEquals(base, base.copy(timestamp = "other-ts"))
        Assert.assertNotEquals(base, base.copy(timestampInMS = 9999L))
        Assert.assertNotEquals(base, base.copy(processingTimeInMS = 9999L))
    }

    @Test
    fun hashCode_isConsistentWithEquals() {
        val first = LogMessageEntity(1L, "DEBUG", "message", "{}", "timestamp", 1000L, 2000L)
        val second = LogMessageEntity(99L, "DEBUG", "message", "{}", "timestamp", 1000L, 2000L)
        val different = first.copy(level = "WARN")

        Assert.assertEquals(first.hashCode(), second.hashCode())
        Assert.assertNotEquals(first.hashCode(), different.hashCode())
    }
}
