package io.justtrack

import org.json.JSONException
import org.json.JSONObject
import java.util.Date

internal class BreadCrumb(
    internal val message: String,
    internal val category: String,
    internal val level: LogLevel,
    internal val timeStamp: Date,
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

    override fun toString(): String {
        return (
            "BreadCrumb{" +
                "message='" + message + '\'' +
                ", category='" + category + '\'' +
                ", level=" + level +
                ", timeStamp=" + timeStamp +
                '}'
            )
    }
}
