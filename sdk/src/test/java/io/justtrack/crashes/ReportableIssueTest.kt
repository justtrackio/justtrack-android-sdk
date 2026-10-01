package io.justtrack.crashes

import io.justtrack.Metric
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ReportableIssueTest {

    private lateinit var logger: Logger

    @Before
    fun setUp() {
        logger = mock()
    }

    // region report() – non-internal crash (dropped)

    @Test
    fun `report drops crash when stack trace is not internal and crashType is not WRAPPER_CRASH`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("com.external.SomeClass.method(SomeClass.kt:10)"),
            timeStamp = "2024-01-01T00:00:00Z",
            timeStampMS = 1000L,
            name = "SomeCrash",
        )

        issue.report(logger)

        verify(logger).info(eq("Application crash dropped at reportCrash"), any<LoggerFields>())
        verify(logger, never()).publishMetric(any(), any(), any<LoggerFields>())
        verify(logger, never()).error(any<String>(), any<LoggerFields>())
    }

    @Test
    fun `report drops crash when stack trace is not internal and crashType is null`() {
        val issue = ReportableIssueTestable(
            crashType = null,
            stackTrace = listOf("com.external.SomeClass.method(SomeClass.kt:10)"),
            timeStamp = null,
            timeStampMS = null,
            name = "SomeCrash",
        )

        issue.report(logger)

        verify(logger).info(eq("Application crash dropped at reportCrash"), any<LoggerFields>())
        verify(logger, never()).publishMetric(any(), any(), any<LoggerFields>())
    }

    // endregion

    // region report() – internal crash (reported)

    @Test
    fun `report publishes metric and logs error for internal crash`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.SomeClass.method(SomeClass.kt:10)"),
            timeStamp = "2024-01-01T00:00:00Z",
            timeStampMS = 1000L,
            name = "SomeCrash",
        )

        issue.report(logger)

        verify(logger).publishMetric(any<Metric>(), eq(1.0), any<LoggerFields>())
        verify(logger).error(eq("Application shutdown due to crash"), any<LoggerFields>())
        verify(logger, never()).info(any<String>(), any<LoggerFields>())
    }

    @Test
    fun `report uses crashType name in metric fields for internal crash`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.ANR,
            stackTrace = listOf("io.justtrack.SomeClass.method(SomeClass.kt:10)"),
            timeStamp = null,
            timeStampMS = null,
            name = "ANRCrash",
        )

        issue.report(logger)

        val fieldsCaptor = argumentCaptor<LoggerFieldsBuilder>()
        verify(logger).publishMetric(any(), any(), fieldsCaptor.capture())
        val fields = fieldsCaptor.firstValue.fields
        assertEquals(CrashType.ANR.name, fields["type"])
    }

    @Test
    fun `report falls back to NORMAL_CRASH type name when crashType is null for internal crash`() {
        // Stack trace is empty -> InternalCrashChecker.isInternalCrash returns true
        val issue = ReportableIssueTestable(
            crashType = null,
            stackTrace = emptyList(),
            timeStamp = null,
            timeStampMS = null,
            name = "UnknownCrash",
        )

        issue.report(logger)

        val fieldsCaptor = argumentCaptor<LoggerFieldsBuilder>()
        verify(logger).publishMetric(any(), any(), fieldsCaptor.capture())
        val fields = fieldsCaptor.firstValue.fields
        assertEquals(CrashType.NORMAL_CRASH.toString(), fields["type"])
    }

    @Test
    fun `report forces reporting when crashType is WRAPPER_CRASH regardless of stack trace`() {
        // Stack trace is external, but WRAPPER_CRASH bypasses the filter
        val issue = ReportableIssueTestable(
            crashType = CrashType.WRAPPER_CRASH,
            stackTrace = listOf("com.external.SomeClass.method(SomeClass.kt:10)"),
            timeStamp = null,
            timeStampMS = null,
            name = "WrapperCrash",
        )

        issue.report(logger)

        verify(logger).publishMetric(any<Metric>(), eq(1.0), any<LoggerFields>())
        verify(logger).error(eq("Application shutdown due to crash"), any<LoggerFields>())
        verify(logger, never()).info(any<String>(), any<LoggerFields>())
    }

    // endregion

    // region report() – breadcrumb handling

    @Test
    fun `report does not log breadcrumb when breadcrumbs is null`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.SomeClass.method"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
            breadcrumbs = null,
        )

        issue.report(logger)

        verify(logger, never()).error(eq("Application shutdown with breadcrumb"), any<LoggerFields>())
    }

    @Test
    fun `report does not log breadcrumb when breadcrumbs is empty array string`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.SomeClass.method"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
            breadcrumbs = "[]",
        )

        issue.report(logger)

        verify(logger, never()).error(eq("Application shutdown with breadcrumb"), any<LoggerFields>())
    }

    @Test
    fun `report logs one breadcrumb chunk when breadcrumbs fit within chunk size`() {
        val breadcrumbs = "[{\"event\":\"click\"}]"
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.SomeClass.method"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
            breadcrumbs = breadcrumbs,
        )

        issue.report(logger)

        verify(logger, times(1)).error(eq("Application shutdown with breadcrumb"), any<LoggerFields>())
    }

    @Test
    fun `report splits breadcrumbs into multiple chunks when exceeding chunk size`() {
        // Build a breadcrumb string longer than 4000 characters
        val breadcrumbs = "x".repeat(8001)
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.SomeClass.method"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
            breadcrumbs = breadcrumbs,
        )

        issue.report(logger)

        // 8001 chars / 4000 chunk size = 3 chunks
        verify(logger, times(3)).error(eq("Application shutdown with breadcrumb"), any<LoggerFields>())
    }

    // endregion

    // region addFields()

    @Test
    fun `addFields includes timestamp when timeStamp is not null`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.SomeClass.method"),
            timeStamp = "2024-06-01T12:00:00Z",
            timeStampMS = 1000L,
            name = "Crash",
        )
        val fields = LoggerFieldsBuilder()

        issue.addFields(fields, logger)

        val result = fields.fields
        assertEquals("2024-06-01T12:00:00Z", result["timestamp"])
        assertEquals("io.justtrack.SomeClass.method", result["stack_trace"])
    }

    @Test
    fun `addFields omits timestamp when timeStamp is null`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.SomeClass.method"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
        )
        val fields = LoggerFieldsBuilder()

        issue.addFields(fields, logger)

        val result = fields.fields
        assertNull(result["timestamp"])
        assertEquals("io.justtrack.SomeClass.method", result["stack_trace"])
    }

    @Test
    fun `addFields joins multiple stack trace entries with comma and space`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.A.a(A.kt:1)", "io.justtrack.B.b(B.kt:2)"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
        )
        val fields = LoggerFieldsBuilder()

        issue.addFields(fields, logger)

        val result = fields.fields
        assertEquals("io.justtrack.A.a(A.kt:1), io.justtrack.B.b(B.kt:2)", result["stack_trace"])
    }

    // endregion

    // region toJson()

    @Test
    fun `toJson returns all fields when all values are provided`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NATIVE_CRASH,
            stackTrace = listOf("io.justtrack.A.a(A.kt:1)"),
            timeStamp = "2024-01-01T00:00:00Z",
            timeStampMS = 123456789L,
            name = "NativeCrash",
            breadcrumbs = "[{\"event\":\"tap\"}]",
            reason = "SIGSEGV",
        )

        val json = issue.toJson()

        assertEquals(CrashType.NATIVE_CRASH.name, json.getString("crashType"))
        assertEquals("2024-01-01T00:00:00Z", json.getString("timeStamp"))
        assertEquals(123456789L, json.getLong("timeStampMS"))
        assertEquals("NativeCrash", json.getString("name"))
        assertEquals("[{\"event\":\"tap\"}]", json.getString("breadcrumb"))
        assertEquals("SIGSEGV", json.getString("reason"))

        val normalIssue = ReportableIssueTestable(
            crashType = null,
            stackTrace = listOf("io.justtrack.A.a(A.kt:1)"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
        )

        val normalCrashJson = normalIssue.toJson()

        assertEquals(CrashType.NORMAL_CRASH.name, normalCrashJson.getString("crashType"))
    }

    @Test
    fun `report uses NORMAL_CRASH toString as fallback in metric fields when crashType is null`() {
        val issue = ReportableIssueTestable(
            crashType = null,
            stackTrace = listOf("io.justtrack.SomeClass.method(SomeClass.kt:10)"),
            timeStamp = null,
            timeStampMS = null,
            name = "NullTypeCrash",
        )

        issue.report(logger)

        val fieldsCaptor = argumentCaptor<LoggerFieldsBuilder>()
        verify(logger).publishMetric(any(), any(), fieldsCaptor.capture())
        val fields = fieldsCaptor.firstValue.fields
        assertEquals(CrashType.NORMAL_CRASH.toString(), fields["type"])
    }

    @Test
    fun `report uses NORMAL_CRASH toString fallback in metric when crashType is null with internal stack trace`() {
        val issue = ReportableIssueTestable(
            crashType = null,
            stackTrace = listOf("io.justtrack.SomeClass.method(SomeClass.kt:10)"),
            timeStamp = null,
            timeStampMS = null,
            name = "NullTypeCrash",
        )

        issue.report(logger)

        val fieldsCaptor = argumentCaptor<LoggerFieldsBuilder>()
        verify(logger).publishMetric(any(), any(), fieldsCaptor.capture())
        val fields = fieldsCaptor.firstValue.fields
        assertEquals(CrashType.NORMAL_CRASH.toString(), fields["type"])
    }

    @Test
    fun `toJson uses NORMAL_CRASH fallback and includes reason when crashType is null and reason is provided`() {
        val issue = ReportableIssueTestable(
            crashType = null,
            stackTrace = listOf("io.justtrack.A.a(A.kt:1)"),
            timeStamp = "2024-01-01T00:00:00Z",
            timeStampMS = 5000L,
            name = "CrashWithReason",
            breadcrumbs = "[{\"event\":\"tap\"}]",
            reason = "NullPointerException",
        )

        val json = issue.toJson()

        assertEquals(CrashType.NORMAL_CRASH.toString(), json.getString("crashType"))
        assertEquals("NullPointerException", json.getString("reason"))
        assertEquals("[{\"event\":\"tap\"}]", json.getString("breadcrumb"))
        assertEquals("2024-01-01T00:00:00Z", json.getString("timeStamp"))
    }

    @Test
    fun `toJson uses NORMAL_CRASH as crashType string when crashType is null`() {
        val issue = ReportableIssueTestable(
            crashType = null,
            stackTrace = listOf("io.justtrack.A.a(A.kt:1)"),
            timeStamp = null,
            timeStampMS = null,
            name = "UnknownCrash",
        )

        val json = issue.toJson()

        assertEquals(CrashType.NORMAL_CRASH.toString(), json.getString("crashType"))
    }

    @Test
    fun `toJson stores null timeStamp as JSONObject NULL`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.A.a(A.kt:1)"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
        )

        val json = issue.toJson()

        // org.json removes a key when put() is called with null – verify the key is absent
        assertFalse(json.has("timeStamp"))
        assertFalse(json.has("timeStampMS"))
    }

    @Test
    fun `toString returns JSON string representation`() {
        val issue = ReportableIssueTestable(
            crashType = CrashType.NORMAL_CRASH,
            stackTrace = listOf("io.justtrack.A.a(A.kt:1)"),
            timeStamp = null,
            timeStampMS = null,
            name = "Crash",
        )

        val result = issue.toString()

        val parsed = JSONObject(result)
        assertEquals("Crash", parsed.getString("name"))
    }

    // endregion

    // region CrashType enum

    @Test
    fun `CrashType fromId returns correct enum value`() {
        assertEquals(CrashType.NORMAL_CRASH, CrashType.fromId(0))
        assertEquals(CrashType.NATIVE_CRASH, CrashType.fromId(1))
        assertEquals(CrashType.ANR, CrashType.fromId(2))
        assertEquals(CrashType.WRAPPER_CRASH, CrashType.fromId(3))
    }

    @Test
    fun `CrashType fromId returns null for unknown id`() {
        assertNull(CrashType.fromId(999))
    }

    @Test
    fun `CrashType has correct ids`() {
        assertEquals(0, CrashType.NORMAL_CRASH.id)
        assertEquals(1, CrashType.NATIVE_CRASH.id)
        assertEquals(2, CrashType.ANR.id)
        assertEquals(3, CrashType.WRAPPER_CRASH.id)
    }

    // endregion
}

/**
 * Concrete subclass of [ReportableIssue] to allow instantiation in tests.
 * Exposes [addFields] and [toJson] as public since they are `internal open` in the parent.
 */
private class ReportableIssueTestable(
    crashType: CrashType?,
    stackTrace: List<String>,
    timeStamp: String?,
    timeStampMS: Long?,
    name: String,
    breadcrumbs: String? = null,
    reason: String? = null,
) : ReportableIssue(crashType, stackTrace, timeStamp, timeStampMS, name, breadcrumbs, reason)
