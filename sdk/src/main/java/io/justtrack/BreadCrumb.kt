package io.justtrack

import io.justtrack.dtos.LogLevel
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

internal data class BreadCrumb(
    val message: String,
    val category: String,
    val level: LogLevel,
    val timeStamp: Date,
) : JSONEncodable {

    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("message", message)
        obj.put("category", this.category)
        obj.put("level", level)
        obj.put("timestamp", formatter.formatDateMilliseconds(timeStamp))
        return obj
    }
}
