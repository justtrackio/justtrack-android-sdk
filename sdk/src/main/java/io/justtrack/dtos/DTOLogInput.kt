package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

internal data class DTOLogInput(
    val messages: Iterable<DTOLogMessage>,
    val metrics: Iterable<DTOLogMetric>,
    val appVersion: DTOAppVersion,
    val sdkVersion: DTOSdkVersion,
    val clientDate: Date,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        val messagesJsonArray = JSONArray()
        for (data in messages) {
            messagesJsonArray.put(data.toJSON(formatter))
        }
        obj.put("messages", messagesJsonArray)
        val metricsJsonArray = JSONArray()
        for (data in metrics) {
            metricsJsonArray.put(data.toJSON(formatter))
        }
        obj.put("metrics", metricsJsonArray)
        obj.put("appVersion", this.appVersion.toJSON(formatter))
        obj.put("sdkVersion", this.sdkVersion.toJSON(formatter))
        obj.put("clientDate", formatter.formatDateMilliseconds(this.clientDate))
        return obj
    }
}
