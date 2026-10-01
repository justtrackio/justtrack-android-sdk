package io.justtrack.crashes

import io.justtrack.Formatter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NonNativeIssueTest {

    private val emptyBreadcrumbs = JSONArray()

    // region crashType parsing

    @Test
    fun `crashType is resolved from dataJson crashType field`() {
        val dataJson = JSONObject().apply {
            put("crashType", CrashType.ANR.id)
        }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals(CrashType.ANR.name, json.getString("crashType"))
    }

    @Test
    fun `crashType defaults to NORMAL_CRASH when field is absent`() {
        val issue = NonNativeIssue(JSONObject(), emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals(CrashType.NORMAL_CRASH.name, json.getString("crashType"))
    }

    @Test
    fun `crashType defaults to NORMAL_CRASH when id is unrecognised`() {
        val dataJson = JSONObject().apply { put("crashType", 999) }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        // CrashType.fromId(999) returns null → ReportableIssue uses NORMAL_CRASH fallback
        assertEquals(CrashType.NORMAL_CRASH.name, json.getString("crashType"))
    }

    @Test
    fun `crashType WRAPPER_CRASH is preserved when present`() {
        val dataJson = JSONObject().apply { put("crashType", CrashType.WRAPPER_CRASH.id) }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals(CrashType.WRAPPER_CRASH.name, json.getString("crashType"))
    }

    // endregion

    // region stackTrace parsing

    @Test
    fun `stackTrace is parsed from JSONArray`() {
        val dataJson = JSONObject().apply {
            put(
                "stacktrace",
                JSONArray().apply {
                    put("io.justtrack.A.a(A.kt:1)")
                    put("io.justtrack.B.b(B.kt:2)")
                },
            )
        }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)

        @Suppress("UNCHECKED_CAST")
        val stackList = issue.toJson().get("stackTrace") as List<String>
        assertEquals(2, stackList.size)
        assertEquals("io.justtrack.A.a(A.kt:1)", stackList[0])
        assertEquals("io.justtrack.B.b(B.kt:2)", stackList[1])
    }

    @Test
    fun `stackTrace is parsed from String`() {
        val dataJson = JSONObject().apply {
            put("stacktrace", "io.justtrack.A.a(A.kt:1)")
        }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)

        @Suppress("UNCHECKED_CAST")
        val stackList = issue.toJson().get("stackTrace") as List<String>
        assertEquals(1, stackList.size)
        assertEquals("io.justtrack.A.a(A.kt:1)", stackList[0])
    }

    @Test
    fun `stackTrace is empty when stacktrace field is absent`() {
        val issue = NonNativeIssue(JSONObject(), emptyBreadcrumbs, Formatter)

        @Suppress("UNCHECKED_CAST")
        val stackList = issue.toJson().get("stackTrace") as List<*>
        assertEquals(0, stackList.size)
    }

    @Test
    fun `stackTrace is empty when stacktrace field is neither String nor JSONArray`() {
        val dataJson = JSONObject().apply { put("stacktrace", 42) }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)

        @Suppress("UNCHECKED_CAST")
        val stackList = issue.toJson().get("stackTrace") as List<*>
        assertEquals(0, stackList.size)
    }

    // endregion

    // region timestamp parsing

    @Test
    fun `timeStamp is parsed when timestamp is a valid ISO string`() {
        val dataJson = JSONObject().apply {
            put("timestamp", "2024-03-10T08:00:00.000Z")
        }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals("2024-03-10T08:00:00.000Z", json.getString("timeStamp"))
        assertFalse(json.isNull("timeStampMS"))
    }

    @Test
    fun `timeStamp is null when timestamp field is absent`() {
        val issue = NonNativeIssue(JSONObject(), emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertFalse(json.has("timeStamp"))
        assertFalse(json.has("timeStampMS"))
    }

    @Test
    fun `timeStamp is null when timestamp is not a parseable string`() {
        val dataJson = JSONObject().apply { put("timestamp", "not-a-date") }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertFalse(json.has("timeStamp"))
        assertFalse(json.has("timeStampMS"))
    }

    @Test
    fun `timeStamp is null when timestamp field is an integer`() {
        val dataJson = JSONObject().apply { put("timestamp", 99999) }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertFalse(json.has("timeStamp"))
        assertFalse(json.has("timeStampMS"))
    }

    // endregion

    // region name parsing

    @Test
    fun `name is read from dataJson name field`() {
        val dataJson = JSONObject().apply { put("name", "IllegalStateException") }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals("IllegalStateException", json.getString("name"))
    }

    @Test
    fun `name is empty string when field is absent`() {
        val issue = NonNativeIssue(JSONObject(), emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals("", json.getString("name"))
    }

    // endregion

    // region reason parsing

    @Test
    fun `reason is read from dataJson reason field`() {
        val dataJson = JSONObject().apply { put("reason", "index out of bounds") }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals("index out of bounds", json.getString("reason"))
    }

    @Test
    fun `reason is empty string when field is absent`() {
        val issue = NonNativeIssue(JSONObject(), emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals("", json.getString("reason"))
    }

    // endregion

    // region breadcrumbs

    @Test
    fun `breadcrumbs is set from breadcrumbJson toString`() {
        val breadcrumbs = JSONArray().apply {
            put(JSONObject().apply { put("event", "click") })
        }

        val issue = NonNativeIssue(JSONObject(), breadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals(breadcrumbs.toString(), json.getString("breadcrumb"))
    }

    @Test
    fun `breadcrumbs is empty array string when breadcrumbJson is empty`() {
        val issue = NonNativeIssue(JSONObject(), emptyBreadcrumbs, Formatter)
        val json = issue.toJson()

        assertEquals("[]", json.getString("breadcrumb"))
    }

    // endregion

    // region formatter property

    @Test
    fun `formatter property is accessible`() {
        val issue = NonNativeIssue(JSONObject(), emptyBreadcrumbs, Formatter)
        assertEquals(Formatter, issue.formatter)
    }

    // endregion

    // region toString

    @Test
    fun `toString returns JSON string representation`() {
        val dataJson = JSONObject().apply {
            put("name", "RuntimeException")
            put("crashType", CrashType.NORMAL_CRASH.id)
        }

        val issue = NonNativeIssue(dataJson, emptyBreadcrumbs, Formatter)
        val parsed = JSONObject(issue.toString())

        assertEquals("RuntimeException", parsed.getString("name"))
        assertEquals(CrashType.NORMAL_CRASH.name, parsed.getString("crashType"))
    }

    // endregion
}
