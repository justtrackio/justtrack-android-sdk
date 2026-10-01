package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.util.Date

class DTOLogInputTest {
    private val clientDate = Date(1_592_228_819_000L)
    private fun makeAppVersion() = DTOAppVersion(name = "1.0.0", code = "100")
    private fun makeSdkVersion() = DTOSdkVersion(1, 0, 0, "1.0.0", "android", null)
    private fun makeMessage(msg: String = "test msg") = DTOLogMessage(
        level = LogLevel.INFO,
        message = msg,
        fields = JSONObject(),
        timestamp = clientDate,
    )
    private fun makeMetric(name: String = "latency") = DTOLogMetric(
        metric = name,
        dimensions = JSONObject(),
        value = 1.0,
        unit = "ms",
        timestamp = clientDate,
    )

    @Test
    @Throws(JSONException::class)
    fun constructorStoresFields() {
        val messages = listOf(makeMessage())
        val metrics = listOf(makeMetric())
        val appVersion = makeAppVersion()
        val sdkVersion = makeSdkVersion()

        val dto = DTOLogInput(
            messages = messages,
            metrics = metrics,
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            clientDate = clientDate,
        )

        Assert.assertEquals(appVersion, dto.appVersion)
        Assert.assertEquals(sdkVersion, dto.sdkVersion)
        Assert.assertEquals(clientDate, dto.clientDate)
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesAllFields() {
        val dto = DTOLogInput(
            messages = listOf(makeMessage("msg1"), makeMessage("msg2")),
            metrics = listOf(makeMetric("m1")),
            appVersion = makeAppVersion(),
            sdkVersion = makeSdkVersion(),
            clientDate = clientDate,
        )

        val json = dto.toJSON(Formatter)

        val messagesArr = json.getJSONArray("messages")
        Assert.assertEquals(2, messagesArr.length())
        Assert.assertEquals("msg1", messagesArr.getJSONObject(0).getString("message"))
        Assert.assertEquals("msg2", messagesArr.getJSONObject(1).getString("message"))

        val metricsArr = json.getJSONArray("metrics")
        Assert.assertEquals(1, metricsArr.length())
        Assert.assertEquals("m1", metricsArr.getJSONObject(0).getString("metric"))

        Assert.assertEquals("1.0.0", json.getJSONObject("appVersion").getString("name"))
        Assert.assertEquals(1, json.getJSONObject("sdkVersion").getInt("major"))
        Assert.assertEquals(Formatter.formatDateMilliseconds(clientDate), json.getString("clientDate"))
    }

    @Test
    @Throws(JSONException::class)
    fun toJsonSerializesEmptyMessagesAndMetrics() {
        val dto = DTOLogInput(
            messages = emptyList(),
            metrics = emptyList(),
            appVersion = makeAppVersion(),
            sdkVersion = makeSdkVersion(),
            clientDate = clientDate,
        )

        val json = dto.toJSON(Formatter)

        Assert.assertEquals(0, json.getJSONArray("messages").length())
        Assert.assertEquals(0, json.getJSONArray("metrics").length())
    }
}
