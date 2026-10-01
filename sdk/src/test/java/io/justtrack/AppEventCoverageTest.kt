package io.justtrack

import io.justtrack.events.Dimension
import io.justtrack.events.Money
import io.justtrack.events.Unit
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.versions.SdkVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.TreeMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
internal class AppEventCoverageTest {
    private val sdkVersion = TestSdkVersion()

    @Test
    fun constructorsAndSettersBuildExpectedPublishableEvents() {
        val dimensionsEvent = AppEvent("dimension_event", mapOf("key" to "value"))
        assertEquals(mapOf("key" to "value"), dimensionsEvent.dimensions)

        val secondsEvent = AppEvent("seconds_event", 2.5, Unit.SECONDS).build("session", sdkVersion)
        assertEquals(2_500.0, secondsEvent.value, 0.0)
        assertEquals(Unit.MILLISECONDS, secondsEvent.unit)

        val countEvent = AppEvent("count_event", 3.0, Unit.COUNT).build("session", sdkVersion)
        assertEquals(3.0, countEvent.value, 0.0)
        assertEquals(Unit.COUNT, countEvent.unit)

        val moneyEvent = AppEvent("money_event", Money(4.2, "EUR")).build("session", sdkVersion)
        assertEquals(4.2, moneyEvent.value, 0.0)
        assertEquals("EUR", moneyEvent.currency)
        assertEquals(null, moneyEvent.unit)

        val happenedAt = Date(123)
        val internalSecondsEvent = AppEvent("internal_event", 1.5, Unit.SECONDS, null, happenedAt).build("session", sdkVersion)
        assertEquals(1_500.0, internalSecondsEvent.value, 0.0)
        assertEquals(Unit.MILLISECONDS, internalSecondsEvent.unit)
        assertSame(happenedAt, internalSecondsEvent.happenedAt)

        val internalCurrencyEvent = AppEvent("internal_money", 9.0, null, "USD", happenedAt).build("session", sdkVersion)
        assertEquals("USD", internalCurrencyEvent.currency)
        assertEquals(null, internalCurrencyEvent.unit)
    }

    @Test
    fun copyConstructorCreatesIndependentMutableDimensionMap() {
        val original = AppEvent("copy_event")
            .addDimension("first", "value")
            .setValue(Money(1.0, "EUR"))
        original.sessionId = "original-session"

        val copy = AppEvent(original)
        copy.addDimension("second", "value")

        assertEquals(mapOf("first" to "value"), original.dimensions)
        assertEquals(mapOf("first" to "value", "second" to "value"), copy.dimensions)
        assertEquals(original, AppEvent(original))
    }

    @Test
    fun settersReturnSameInstanceAndNormalizeSeconds() {
        val event = AppEvent("setter_event")

        assertSame(event, event.setCount(2.0))
        assertEquals(2.0, event.build("session", sdkVersion).value, 0.0)
        assertEquals(Unit.COUNT, event.build("session", sdkVersion).unit)

        assertSame(event, event.setSeconds(3.0))
        assertEquals(3_000.0, event.build("session", sdkVersion).value, 0.0)
        assertEquals(Unit.MILLISECONDS, event.build("session", sdkVersion).unit)

        assertSame(event, event.setMilliseconds(4.0))
        assertEquals(4.0, event.build("session", sdkVersion).value, 0.0)
        assertEquals(Unit.MILLISECONDS, event.build("session", sdkVersion).unit)

        assertSame(event, event.setValue(Money(5.0, "USD")))
        val built = event.build("session", sdkVersion)
        assertEquals(5.0, built.value, 0.0)
        assertEquals("USD", built.currency)
        assertEquals(null, built.unit)
    }

    @Test
    fun addAndRemoveDimensionsHandleStringAndEnumInputs() {
        val event = AppEvent("dimension_mutation")

        assertSame(event, event.addDimension("custom", "value"))
        assertSame(event, event.addDimension(Dimension.JT_ACTION, "start"))
        assertEquals("value", event.dimensions["custom"])
        assertEquals("start", event.dimensions["jt_action"])

        event.addDimension("custom", null)
        assertFalse(event.dimensions.containsKey("custom"))

        event.addDimension("custom", "value")
        event.addDimension("", "ignored")
        event.addDimension("blank", "")
        assertFalse(event.dimensions.containsKey(""))
        assertFalse(event.dimensions.containsKey("blank"))

        assertSame(event, event.removeDimension(Dimension.JT_ACTION))
        assertSame(event, event.removeDimension("custom"))
        assertSame(event, event.removeDimension(""))
        assertTrue(event.dimensions.isEmpty())
    }

    @Test
    fun toStringSkipsBlankDimensionValues() {
        val event = AppEvent("blank_dimension")
        event.dimensions["blank"] = ""

        val string = event.toString()

        assertTrue(string.contains("dimensions = ["))
        assertFalse(string.contains("blank ="))
    }

    @Test
    fun validateAcceptsValidAndTokenNullDimension() {
        AppEvent("valid_event")
            .addDimension(Dimension.JT_TOKEN, null)
            .setValue(Money(1.0, "EUR"))
            .validate()
    }

    @Test
    fun validateRejectsInvalidNamesDimensionsValuesAndMoney() {
        assertInvalidField { AppEvent("").validate() }
        assertInvalidField { AppEvent("a".repeat(256)).validate() }
        assertInvalidField { AppEvent("bad\u0100").validate() }
        assertInvalidField { AppEvent("event", mapOf("Bad" to "value")).validate() }
        assertInvalidField { AppEvent("event", mapOf("dimension" to "x".repeat(4096))).validate() }
        assertInvalidField { AppEvent("event").setValue(Double.NaN, Unit.COUNT).validate() }
        assertInvalidField { AppEvent("event", Money(1.0, "eur")).validate() }

        val tooManyDimensions = AppEvent("event")
        repeat(AppEvent.MAX_DIMENSION_SIZE + 1) {
            tooManyDimensions.addDimension("dimension_$it", "value")
        }
        assertInvalidField { tooManyDimensions.validate() }
    }

    @Test
    fun buildUsesExplicitSessionWhenPresentAndNowWhenHappenedAtMissing() {
        val before = System.currentTimeMillis()
        val event = AppEvent("build_event")
        event.sessionId = "explicit-session"

        val built = event.build("fallback-session", sdkVersion)

        assertEquals("explicit-session", built.sessionId)
        assertTrue(built.happenedAt.time >= before)
        assertTrue(built.happenedAt.time <= System.currentTimeMillis())
    }

    @Test
    fun toStringIncludesDimensionsValueUnitCurrencySessionAndDateBranches() {
        val dated = Date(0)
        val withUnit = AppEvent("string_unit", 2.0, Unit.COUNT, null, dated)
            .addDimension("first", "value")
            .addDimension("empty_value", null)
        withUnit.sessionId = "session-id"
        val unitString = withUnit.toString()
        assertTrue(unitString.contains("[AppEvent string_unit"))
        assertTrue(unitString.contains("first = value"))
        assertTrue(unitString.contains("count"))
        assertTrue(unitString.contains("sessionId = session-id"))
        assertTrue(unitString.contains(dated.toString()))

        val withCurrency = AppEvent("string_currency", Money(3.0, "EUR")).toString()
        assertTrue(withCurrency.contains("EUR"))

        val withNoUnitOrCurrency = AppEvent("string_null").toString()
        assertTrue(withNoUnitOrCurrency.contains("null"))
        assertTrue(withNoUnitOrCurrency.contains("happenedAt = now"))
    }

    @Test
    fun equalsCoversAllFieldBranches() {
        val base = AppEvent("equals_event", 1.0, Unit.COUNT, null, Date(1))
            .addDimension("dimension", "value")
        base.sessionId = "session"

        assertEquals(base, base)
        assertFalse(base.equals("not-event"))
        assertEquals(base, AppEvent(base))
        assertNotEquals(base, AppEvent("other", 1.0, Unit.COUNT, null, Date(1)).addDimension("dimension", "value"))
        assertNotEquals(base, AppEvent("equals_event", 1.0, Unit.COUNT, null, Date(1)).addDimension("other", "value"))
        assertNotEquals(base, AppEvent("equals_event", 2.0, Unit.COUNT, null, Date(1)).addDimension("dimension", "value"))
        assertNotEquals(base, AppEvent("equals_event", 1.0, Unit.MILLISECONDS, null, Date(1)).addDimension("dimension", "value"))
        assertNotEquals(base, AppEvent("equals_event", 1.0, null, "EUR", Date(1)).addDimension("dimension", "value"))

        val differentSession = AppEvent(base)
        differentSession.sessionId = "other-session"
        assertNotEquals(base, differentSession)

        assertNotEquals(base, AppEvent("equals_event", 1.0, Unit.COUNT, null, Date(2)).addDimension("dimension", "value"))
        assertEquals(AppEvent("no_session"), AppEvent("no_session"))
        assertNotEquals(AppEvent("no_session"), AppEvent("no_session").also { it.sessionId = "session" })
        assertEquals(AppEvent("no_date"), AppEvent("no_date"))
        assertNotEquals(AppEvent("no_date"), AppEvent("no_date", 0.0, null, null, Date(1)))
        assertEquals(AppEvent("no_currency"), AppEvent("no_currency"))
        assertNotEquals(AppEvent("no_currency"), AppEvent("no_currency", Money(0.0, "EUR")))
    }

    @Test
    fun hashCodeMatchesManualCalculationOnModernSdk() {
        val event = AppEvent("hash_event", 1.25, Unit.COUNT, null, Date(5))
            .addDimension("dimension", "value")
        event.sessionId = "session"

        val expected = manualHashCode(
            name = "hash_event",
            dimensions = event.dimensions,
            valueHash = java.lang.Double.hashCode(1.25),
            unit = Unit.COUNT,
            currency = null,
            sessionId = "session",
            happenedAt = Date(5),
        )

        assertEquals(expected, event.hashCode())
        assertEquals(event.hashCode(), AppEvent(event).hashCode())
    }

    @Test
    @Config(sdk = [23])
    fun hashCodeUsesLegacyDoubleHashBeforeNougat() {
        val event = AppEvent("legacy_hash", 9.75, null, "EUR", Date(9))

        val expected = manualHashCode(
            name = "legacy_hash",
            dimensions = event.dimensions,
            valueHash = 9.75.toInt(),
            unit = null,
            currency = "EUR",
            sessionId = null,
            happenedAt = Date(9),
        )

        assertEquals(expected, event.hashCode())
    }

    @Test
    fun hashCodeCoversNullOptionalFieldsOnModernSdk() {
        val event = AppEvent("hash_nulls")

        val expected = manualHashCode(
            name = "hash_nulls",
            dimensions = event.dimensions,
            valueHash = java.lang.Double.hashCode(0.0),
            unit = null,
            currency = null,
            sessionId = null,
            happenedAt = null,
        )

        assertEquals(expected, event.hashCode())
    }

    @Test
    fun hashCodeCoversSingleOptionalFieldsOnModernSdk() {
        val unitEvent = AppEvent("hash_unit", 0.0, Unit.COUNT, null, null)
        assertEquals(
            manualHashCode("hash_unit", unitEvent.dimensions, java.lang.Double.hashCode(0.0), Unit.COUNT, null, null, null),
            unitEvent.hashCode(),
        )

        val currencyEvent = AppEvent("hash_currency", 0.0, null, "EUR", null)
        assertEquals(
            manualHashCode("hash_currency", currencyEvent.dimensions, java.lang.Double.hashCode(0.0), null, "EUR", null, null),
            currencyEvent.hashCode(),
        )

        val sessionEvent = AppEvent("hash_session")
        sessionEvent.sessionId = "session"
        assertEquals(
            manualHashCode("hash_session", sessionEvent.dimensions, java.lang.Double.hashCode(0.0), null, null, "session", null),
            sessionEvent.hashCode(),
        )
    }

    @Test
    fun privateDefaultConstructorBridgeIsCovered() {
        val constructor = AppEvent::class.java.declaredConstructors.first { it.parameterTypes.size == 9 }
        constructor.isAccessible = true

        val event = constructor.newInstance(
            "bridge_event",
            TreeMap<String?, String?>(),
            0.0,
            null,
            null,
            null,
            null,
            0b0001110,
            null,
        ) as AppEvent

        assertEquals("bridge_event", event.name)
        assertTrue(event.dimensions.isEmpty())
    }

    private fun assertInvalidField(block: () -> kotlin.Unit) {
        try {
            block()
            fail("Expected InvalidFieldException")
        } catch (_: InvalidFieldException) {
            // Expected.
        }
    }

    private fun manualHashCode(
        name: String,
        dimensions: Map<String?, String?>,
        valueHash: Int,
        unit: Unit?,
        currency: String?,
        sessionId: String?,
        happenedAt: Date?,
    ): Int {
        var hashCode = name.hashCode()
        hashCode = 31 * hashCode + dimensions.hashCode()
        hashCode = 31 * hashCode + valueHash
        hashCode = 31 * hashCode + (unit?.hashCode() ?: 0)
        hashCode = 31 * hashCode + (currency?.hashCode() ?: 0)
        hashCode = 31 * hashCode + (sessionId?.hashCode() ?: 0)
        hashCode = 31 * hashCode + (happenedAt?.hashCode() ?: 0)
        return hashCode
    }

    private class TestSdkVersion : SdkVersion {
        override val platformType: PlatformType = PlatformType.ANDROID
        override val major: Int = 1
        override val minor: Int = 2
        override val patch: Int = 3
        override val name: String = "1.2.3-test"
    }
}
