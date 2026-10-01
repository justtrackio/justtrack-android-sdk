package io.justtrack

import io.justtrack.dtos.DTOAppEventEvent
import io.justtrack.events.Unit
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date
import java.util.UUID

class DTOAppEventEventTest {
    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000123")
        val happenedAt = Date(1_592_228_819_000L)
        val dimensions = JSONObject().apply { put("level", "hard") }
        val event = DTOAppEventEvent(
            id,
            "session_completed",
            dimensions,
            42.5,
            Unit.SECONDS,
            "EUR",
            "session-id",
            happenedAt,
            99L,
        )

        val json = event.toJSON(Formatter)

        Assert.assertEquals(id.toString(), json.getString("id"))
        Assert.assertEquals("session_completed", json.getString("name"))
        Assert.assertEquals("hard", json.getJSONObject("dimensions").getString("level"))
        Assert.assertEquals("seconds", json.getString("unit"))
        Assert.assertEquals("EUR", json.getString("currency"))
        Assert.assertEquals(42.5, json.getDouble("value"), 0.0)
        Assert.assertEquals("session-id", json.getString("sessionId"))
        Assert.assertEquals(
            Formatter.formatDateMilliseconds(happenedAt),
            json.getString("happenedAt"),
        )
        Assert.assertEquals(99L, json.getLong("sequenceNumber"))
        Assert.assertEquals(id, event.id)
        Assert.assertEquals("session_completed", event.name)
        Assert.assertEquals(dimensions, event.dimensions)
        Assert.assertEquals(42.5, event.value, 0.0)
        Assert.assertEquals(Unit.SECONDS, event.unit)
        Assert.assertEquals("EUR", event.currency)
        Assert.assertEquals("session-id", event.sessionId)
        Assert.assertEquals(happenedAt, event.happenedAt)
        Assert.assertEquals(99L, event.sequenceNumber)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonOmitsValueWhenNoUnitOrCurrency() {
        val event = DTOAppEventEvent(
            UUID.fromString("00000000-0000-0000-0000-000000000456"),
            "purchase",
            null,
            13.39,
            null,
            null,
            "session-id",
            Date(1_592_228_819_000L),
            1L,
        )

        val json = event.toJSON(Formatter)

        Assert.assertFalse(json.has("unit"))
        Assert.assertFalse(json.has("currency"))
        Assert.assertFalse(json.has("value"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonOmitsDimensionsWhenEmpty() {
        val event = DTOAppEventEvent(
            UUID.fromString("00000000-0000-0000-0000-000000000789"),
            "level_up",
            JSONObject(),
            10.0,
            Unit.COUNT,
            "USD",
            "session-id",
            Date(1_592_228_819_000L),
            5L,
        )

        val json = event.toJSON(Formatter)

        Assert.assertFalse(json.has("dimensions"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesCurrencyValueWithoutUnit() {
        val event = DTOAppEventEvent(
            UUID.fromString("00000000-0000-0000-0000-000000000999"),
            "purchase",
            null,
            7.0,
            null,
            "USD",
            "session-id",
            Date(1_592_228_819_000L),
            6L,
        )

        val json = event.toJSON(Formatter)

        Assert.assertEquals(7.0, json.getDouble("value"), 0.0)
        Assert.assertFalse(json.has("unit"))
        Assert.assertEquals("USD", json.getString("currency"))
    }
}
