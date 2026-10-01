package io.justtrack.crashes

import io.justtrack.Formatter
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class NativeIssueTest {

    private lateinit var logger: Logger

    @Before
    fun setUp() {
        logger = mock()
    }

    // region NativeIssue – stackTrace parsing

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
            put("signal", NativeSignal.SIGSEGV.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        // stackTrace is stored as a List in JSONObject – retrieve it as a list
        @Suppress("UNCHECKED_CAST")
        val stackList = json.get("stackTrace") as List<String>
        assertEquals(2, stackList.size)
        assertEquals("io.justtrack.A.a(A.kt:1)", stackList[0])
        assertEquals("io.justtrack.B.b(B.kt:2)", stackList[1])
    }

    @Test
    fun `stackTrace is parsed from String`() {
        val dataJson = JSONObject().apply {
            put("stacktrace", "io.justtrack.A.a(A.kt:1)")
            put("signal", NativeSignal.SIGSEGV.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        @Suppress("UNCHECKED_CAST")
        val stackList = json.get("stackTrace") as List<String>
        assertEquals(1, stackList.size)
        assertEquals("io.justtrack.A.a(A.kt:1)", stackList[0])
    }

    @Test
    fun `stackTrace is empty list when stacktrace field is absent`() {
        val dataJson = JSONObject().apply {
            put("signal", NativeSignal.SIGSEGV.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        @Suppress("UNCHECKED_CAST")
        val stackList = json.get("stackTrace") as List<*>
        assertEquals(0, stackList.size)
    }

    @Test
    fun `stackTrace is empty list when stacktrace field is neither String nor JSONArray`() {
        val dataJson = JSONObject().apply {
            put("stacktrace", 42)
            put("signal", NativeSignal.SIGSEGV.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        @Suppress("UNCHECKED_CAST")
        val stackList = json.get("stackTrace") as List<*>
        assertEquals(0, stackList.size)
    }

    // endregion

    // region NativeIssue – timestamp parsing

    @Test
    fun `timeStamp is parsed when timestamp is a valid ISO string`() {
        val dataJson = JSONObject().apply {
            put("timestamp", "2024-01-15T10:30:00.000Z")
            put("signal", NativeSignal.SIGFPE.id)
            put("stacktrace", "io.justtrack.A.a")
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        assertEquals("2024-01-15T10:30:00.000Z", json.getString("timeStamp"))
        assertNotNull(json.opt("timeStampMS"))
    }

    @Test
    fun `timeStamp is null when timestamp field is absent`() {
        val dataJson = JSONObject().apply {
            put("signal", NativeSignal.SIGFPE.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        assertFalse(json.has("timeStamp"))
        assertFalse(json.has("timeStampMS"))
    }

    @Test
    fun `timeStamp is null when timestamp field is not a parseable string`() {
        val dataJson = JSONObject().apply {
            put("timestamp", "not-a-date")
            put("signal", NativeSignal.SIGFPE.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        assertFalse(json.has("timeStamp"))
        assertFalse(json.has("timeStampMS"))
    }

    @Test
    fun `timeStamp is null when timestamp field is an integer instead of string`() {
        val dataJson = JSONObject().apply {
            put("timestamp", 99999)
            put("signal", NativeSignal.SIGFPE.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        assertFalse(json.has("timeStamp"))
        assertFalse(json.has("timeStampMS"))
    }

    // endregion

    // region NativeIssue – name (signal) resolution

    @Test
    fun `name is set to known NativeSignal name when signal id matches`() {
        val dataJson = JSONObject().apply {
            put("signal", NativeSignal.SIGSEGV.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        assertEquals(NativeSignal.SIGSEGV.name, json.getString("name"))
    }

    @Test
    fun `name falls back to UNKNOWN_SIGNAL when signal id is unrecognised`() {
        val dataJson = JSONObject().apply {
            put("signal", 999)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        assertEquals(NativeSignal.UNKNOWN_SIGNAL.name, json.getString("name"))
    }

    @Test
    fun `name falls back to UNKNOWN_SIGNAL when signal field is absent`() {
        val dataJson = JSONObject()

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        // optInt defaults to 0 which is not a known signal
        assertEquals(NativeSignal.UNKNOWN_SIGNAL.name, json.getString("name"))
    }

    // endregion

    // region NativeIssue – toJson()

    @Test
    fun `toJson includes signal field as signalInfo toString`() {
        val dataJson = JSONObject().apply {
            put("signal", NativeSignal.SIGABRT.id)
            put("error", 2)
            put("code", 3)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val json = issue.toJson()

        // NativeIssue.toJson() stores signalInfo.toString() (data class representation) under "signal"
        val signalStr = json.getString("signal")
        assertEquals(issue.signalInfo.toString(), signalStr)
    }

    @Test
    fun `toJson includes crashType as NATIVE_CRASH`() {
        val issue = NativeIssue(JSONObject(), Formatter)
        val json = issue.toJson()

        assertEquals(CrashType.NATIVE_CRASH.name, json.getString("crashType"))
    }

    // endregion

    // region NativeIssue – addFields()

    @Test
    fun `addFields adds native_signal_info field`() {
        val dataJson = JSONObject().apply {
            put("signal", NativeSignal.SIGBUS.id)
            put("pid", 1234)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val fields = LoggerFieldsBuilder()
        issue.addFields(fields, logger)

        assertNotNull(fields.fields["native_signal_info"])
        val signalInfoJson = JSONObject(fields.fields["native_signal_info"]!!)
        assertEquals(NativeSignal.SIGBUS.id, signalInfoJson.getInt("signal"))
    }

    @Test
    fun `addFields also includes inherited stack_trace field`() {
        val dataJson = JSONObject().apply {
            put("stacktrace", "io.justtrack.A.a(A.kt:1)")
            put("signal", NativeSignal.SIGSEGV.id)
        }

        val issue = NativeIssue(dataJson, Formatter)
        val fields = LoggerFieldsBuilder()
        issue.addFields(fields, logger)

        assertEquals("io.justtrack.A.a(A.kt:1)", fields.fields["stack_trace"])
    }

    // endregion

    // region NativeIssue – signalInfo field (@VisibleForTesting)

    @Test
    fun `signalInfo is populated from dataJson`() {
        val dataJson = JSONObject().apply {
            put("signal", 11)
            put("error", 1)
            put("code", 2)
            put("pid", 100)
            put("uid", 200)
            put("status", 0)
            put("addr", 300)
            put("value", 400)
            put("band", 500)
        }

        val issue = NativeIssue(dataJson, Formatter)

        assertEquals(Formatter, issue.formatter)
        assertEquals(SignalInfo(11, 1, 2, 100, 200, 0, 300, 400, 500), issue.signalInfo)
    }

    // endregion

    // region SignalInfo – constructor from JSONObject

    @Test
    fun `SignalInfo from JSONObject parses all fields`() {
        val json = JSONObject().apply {
            put("signal", 11)
            put("error", 1)
            put("code", 2)
            put("pid", 100)
            put("uid", 200)
            put("status", 0)
            put("addr", 300)
            put("value", 400)
            put("band", 500)
        }

        val info = SignalInfo(json)

        assertEquals(11, info.signal)
        assertEquals(1, info.error)
        assertEquals(2, info.code)
        assertEquals(100, info.pid)
        assertEquals(200, info.uid)
        assertEquals(0, info.status)
        assertEquals(300, info.addr)
        assertEquals(400, info.value)
        assertEquals(500, info.band)
    }

    @Test
    fun `SignalInfo from JSONObject sets fields to null when keys are absent`() {
        val info = SignalInfo(JSONObject())

        assertNull(info.signal)
        assertNull(info.error)
        assertNull(info.code)
        assertNull(info.pid)
        assertNull(info.uid)
        assertNull(info.status)
        assertNull(info.addr)
        assertNull(info.value)
        assertNull(info.band)
    }

    @Test
    fun `SignalInfo from JSONObject sets fields to null when values are wrong type`() {
        val json = JSONObject().apply {
            put("signal", "not-an-int")
            put("error", "not-an-int")
        }

        val info = SignalInfo(json)

        assertNull(info.signal)
        assertNull(info.error)
    }

    // endregion

    // region SignalInfo – toJson()

    @Test
    fun `SignalInfo toJson includes all provided fields`() {
        val info = SignalInfo(signal = 11, error = 1, code = 2, pid = 100, uid = 200, status = 0, addr = 300, value = 400, band = 500)

        val json = info.toJson()

        assertEquals(11, json.getInt("signal"))
        assertEquals(1, json.getInt("error"))
        assertEquals(2, json.getInt("code"))
        assertEquals(100, json.getInt("pid"))
        assertEquals(200, json.getInt("uid"))
        assertEquals(0, json.getInt("status"))
        assertEquals(300, json.getInt("addr"))
        assertEquals(400, json.getInt("value"))
        assertEquals(500, json.getInt("band"))
    }

    @Test
    fun `SignalInfo toJson stores null fields as absent keys`() {
        val info = SignalInfo(signal = null, error = null, code = null, pid = null, uid = null, status = null, addr = null, value = null, band = null)

        val json = info.toJson()

        assertFalse(json.has("signal"))
        assertFalse(json.has("error"))
        assertFalse(json.has("pid"))
    }

    @Test
    fun `SignalInfo toString is data class string representation`() {
        val info = SignalInfo(signal = 8, error = null, code = null, pid = null, uid = null, status = null, addr = null, value = null, band = null)

        val result = info.toString()

        // data class toString contains the class name and field values
        assert(result.contains("SignalInfo")) { "Expected toString to contain 'SignalInfo' but was: $result" }
        assert(result.contains("signal=8")) { "Expected toString to contain 'signal=8' but was: $result" }
    }

    // endregion

    // region NativeSignal enum

    @Test
    fun `NativeSignal fromId returns correct enum value for all signals`() {
        assertEquals(NativeSignal.SIGILL, NativeSignal.fromId(4))
        assertEquals(NativeSignal.SIGTRAP, NativeSignal.fromId(5))
        assertEquals(NativeSignal.SIGABRT, NativeSignal.fromId(6))
        assertEquals(NativeSignal.SIGBUS, NativeSignal.fromId(7))
        assertEquals(NativeSignal.SIGFPE, NativeSignal.fromId(8))
        assertEquals(NativeSignal.SIGSEGV, NativeSignal.fromId(11))
        assertEquals(NativeSignal.SIGPIPE, NativeSignal.fromId(13))
        assertEquals(NativeSignal.UNKNOWN_SIGNAL, NativeSignal.fromId(-1))
    }

    @Test
    fun `NativeSignal fromId returns null for unknown id`() {
        assertNull(NativeSignal.fromId(999))
    }

    @Test
    fun `NativeSignal has correct ids`() {
        assertEquals(4, NativeSignal.SIGILL.id)
        assertEquals(5, NativeSignal.SIGTRAP.id)
        assertEquals(6, NativeSignal.SIGABRT.id)
        assertEquals(7, NativeSignal.SIGBUS.id)
        assertEquals(8, NativeSignal.SIGFPE.id)
        assertEquals(11, NativeSignal.SIGSEGV.id)
        assertEquals(13, NativeSignal.SIGPIPE.id)
        assertEquals(-1, NativeSignal.UNKNOWN_SIGNAL.id)
    }

    // endregion
}
