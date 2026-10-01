package io.justtrack

import io.justtrack.events.Unit
import io.justtrack.versions.SdkVersionImpl
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date
import java.util.TreeMap
import java.util.UUID

internal class UserEventEntityUnitTest {
    private val eventId = UUID.fromString("1db3a1c1-e7e6-4994-949c-23241447e91b")
    private val timestamp = Date(1_700_000_000_000L)

    @Test
    fun constructor_defaultIdProcessingTimeAndSequenceNumber_areMinusOne() {
        val entity = UserEventEntity(
            eventId = eventId.toString(),
            eventName = "event",
            dimensions = "{}",
            sessionId = "session-id",
            value = 3.5,
            unit = "COUNT",
            currency = "EUR",
            timestamp = Formatter.formatDateMilliseconds(timestamp),
            timestampInMS = timestamp.time,
            sdkVersionMajor = 5L,
            sdkVersionMinor = 6L,
            sdkVersionPatch = 7L,
            sdkVersionName = "5.6.7",
        )

        Assert.assertEquals(-1L, entity.id)
        Assert.assertEquals(-1L, entity.processingTimeInMS)
        Assert.assertEquals(-1L, entity.sequenceNumber)
    }

    @Test
    fun hashCode_withNullUnitAndCurrency_usesZero() {
        val entity = userEventEntity().copy(unit = null, currency = null)
        val same = userEventEntity().copy(unit = null, currency = null)

        Assert.assertEquals(entity.hashCode(), same.hashCode())
    }

    @Test
    fun constructorFromStorableEvent_withNullUnit_mapsNullUnit() {
        val storableEvent = StorableEvent(
            id = 1L,
            eventId = eventId,
            event = publishableEvent(unit = null),
            sequenceNumber = 1L,
        )

        val entity = UserEventEntity(storableEvent, Formatter)

        Assert.assertNull(entity.unit)
    }

    @Test
    fun setters_updateMutableFields() {
        val entity = userEventEntity()

        // Exercise the synthetic setters generated for the `var` properties
        entity.id = 99L
        entity.processingTimeInMS = 250L
        entity.sequenceNumber = 42L
        entity.sdkVersionName = "9.8.7"

        Assert.assertEquals(99L, entity.id)
        Assert.assertEquals(250L, entity.processingTimeInMS)
        Assert.assertEquals(42L, entity.sequenceNumber)
        Assert.assertEquals("9.8.7", entity.sdkVersionName)
    }

    @Test
    fun transform_withNullUnit_returnsNullUnit() {
        val entity = userEventEntity().copy(unit = null)

        val event = entity.transform(Formatter, TestLogger(), PlatformType.ANDROID)

        Assert.assertNull(event.event.unit)
    }

    @Test
    fun constructorFromStorableEvent_mapsFields() {
        val storableEvent = StorableEvent(
            id = 7L,
            eventId = eventId,
            event = publishableEvent(),
            sequenceNumber = 12L,
        )

        val entity = UserEventEntity(storableEvent, Formatter)

        Assert.assertEquals(7L, entity.id)
        Assert.assertEquals(eventId.toString(), entity.eventId)
        Assert.assertEquals("event", entity.eventName)
        Assert.assertEquals("value", JSONObject(entity.dimensions).getString("dimension"))
        Assert.assertEquals("session-id", entity.sessionId)
        Assert.assertEquals(3.5, entity.value, 0.0)
        Assert.assertEquals("COUNT", entity.unit)
        Assert.assertEquals("EUR", entity.currency)
        Assert.assertEquals(Formatter.formatDateMilliseconds(timestamp), entity.timestamp)
        Assert.assertEquals(timestamp.time, entity.timestampInMS)
        Assert.assertEquals(12L, entity.sequenceNumber)
        Assert.assertEquals(5L, entity.sdkVersionMajor)
        Assert.assertEquals(6L, entity.sdkVersionMinor)
        Assert.assertEquals(7L, entity.sdkVersionPatch)
        Assert.assertEquals("5.6.7", entity.sdkVersionName)
    }

    @Test
    fun transform_returnsPublishingEvent() {
        val entity = userEventEntity(dimensions = JSONObject(mapOf("dimension" to "value")).toString())

        val event = entity.transform(Formatter, TestLogger(), PlatformType.UNITY)

        Assert.assertEquals(7L, event.id)
        Assert.assertEquals(eventId, event.eventId)
        Assert.assertEquals(12L, event.sequenceNumber)
        Assert.assertEquals("event", event.event.name)
        Assert.assertEquals("value", event.event.dimensions["dimension"])
        Assert.assertEquals(3.5, event.event.value, 0.0)
        Assert.assertEquals(Unit.COUNT, event.event.unit)
        Assert.assertEquals("EUR", event.event.currency)
        Assert.assertEquals("session-id", event.event.sessionId)
        Assert.assertEquals(timestamp, event.event.happenedAt)
        Assert.assertEquals(5, event.event.sdkVersion.major)
        Assert.assertEquals(6, event.event.sdkVersion.minor)
        Assert.assertEquals(7, event.event.sdkVersion.patch)
        Assert.assertEquals("5.6.7", event.event.sdkVersion.name)
        Assert.assertEquals(PlatformType.UNITY, event.event.sdkVersion.platformType)
    }

    @Test
    fun transform_withInvalidDimensionsDateAndUnit_usesFallbacks() {
        val beforeTransform = System.currentTimeMillis()
        val entity = userEventEntity().copy(
            dimensions = "not-json",
            timestamp = "not-date",
            unit = "not-unit",
        )

        val event = entity.transform(Formatter, TestLogger(), PlatformType.ANDROID)

        Assert.assertTrue(event.event.dimensions.isEmpty())
        Assert.assertNull(event.event.unit)
        Assert.assertTrue(event.event.happenedAt.time >= beforeTransform)
        Assert.assertTrue(event.event.happenedAt.time <= System.currentTimeMillis())
    }

    @Test
    fun transform_withInvalidDimensions_usesEmptyDimensions() {
        val entity = userEventEntity().copy(dimensions = "not-json")

        val event = entity.transform(Formatter, TestLogger(), PlatformType.ANDROID)

        Assert.assertTrue(event.event.dimensions.isEmpty())
    }

    @Test
    fun transform_withInvalidTimestamp_usesFallbackDate() {
        val beforeTransform = System.currentTimeMillis()
        val entity = userEventEntity().copy(timestamp = "not-date")

        val event = entity.transform(Formatter, TestLogger(), PlatformType.ANDROID)

        Assert.assertTrue(event.event.happenedAt.time >= beforeTransform)
        Assert.assertTrue(event.event.happenedAt.time <= System.currentTimeMillis())
    }

    @Test
    fun equalsAndHashCode_ignoreId() {
        val first = userEventEntity(id = 1L)
        val second = userEventEntity(id = 2L)

        Assert.assertEquals(first, second)
        Assert.assertEquals(first.hashCode(), second.hashCode())
        Assert.assertNotEquals(first, second.copy(eventName = "other"))
    }

    @Test
    fun equals_sameReference_returnsTrue() {
        val entity = userEventEntity()

        Assert.assertEquals(entity, entity)
    }

    @Test
    fun equals_null_returnsFalse() {
        val entity = userEventEntity()

        Assert.assertNotEquals(entity, null)
    }

    @Test
    fun equals_differentType_returnsFalse() {
        val entity = userEventEntity()

        Assert.assertNotEquals(entity, "not an entity")
    }

    @Test
    fun equals_differentFields_returnsFalse() {
        val base = userEventEntity()
        val otherId = UUID.randomUUID().toString()

        Assert.assertNotEquals(base, base.copy(eventId = otherId))
        Assert.assertNotEquals(base, base.copy(eventName = "other"))
        Assert.assertNotEquals(base, base.copy(dimensions = "{\"a\":1}"))
        Assert.assertNotEquals(base, base.copy(value = 9.9))
        Assert.assertNotEquals(base, base.copy(unit = "REVENUE"))
        Assert.assertNotEquals(base, base.copy(currency = "USD"))
        Assert.assertNotEquals(base, base.copy(sessionId = "other-session"))
        Assert.assertNotEquals(base, base.copy(timestamp = "other-ts"))
        Assert.assertNotEquals(base, base.copy(timestampInMS = 9999L))
        Assert.assertNotEquals(base, base.copy(processingTimeInMS = 9999L))
        Assert.assertNotEquals(base, base.copy(sequenceNumber = 9999L))
        Assert.assertNotEquals(base, base.copy(sdkVersionMajor = 99L))
        Assert.assertNotEquals(base, base.copy(sdkVersionMinor = 99L))
        Assert.assertNotEquals(base, base.copy(sdkVersionPatch = 99L))
        Assert.assertNotEquals(base, base.copy(sdkVersionName = "9.9.9"))
    }

    @Test
    fun hashCode_isConsistentWithEquals() {
        val first = userEventEntity(id = 1L)
        val second = userEventEntity(id = 99L)
        val different = first.copy(eventName = "other")

        Assert.assertEquals(first.hashCode(), second.hashCode())
        Assert.assertNotEquals(first.hashCode(), different.hashCode())
    }

    private fun publishableEvent(unit: Unit? = Unit.COUNT): PublishableAppEvent {
        return PublishableAppEvent(
            "event",
            TreeMap(mapOf("dimension" to "value")),
            3.5,
            unit,
            "EUR",
            "session-id",
            SdkVersionImpl(5, 6, 7, "5.6.7", PlatformType.ANDROID),
            timestamp,
        )
    }

    private fun userEventEntity(id: Long = 7L, processingTimeInMS: Long = -1L, dimensions: String = "{}"): UserEventEntity {
        return UserEventEntity(
            id = id,
            eventId = eventId.toString(),
            eventName = "event",
            dimensions = dimensions,
            sessionId = "session-id",
            value = 3.5,
            unit = "COUNT",
            currency = "EUR",
            timestamp = Formatter.formatDateMilliseconds(timestamp),
            timestampInMS = timestamp.time,
            processingTimeInMS = processingTimeInMS,
            sequenceNumber = 12L,
            sdkVersionMajor = 5L,
            sdkVersionMinor = 6L,
            sdkVersionPatch = 7L,
            sdkVersionName = "5.6.7",
        )
    }
}
