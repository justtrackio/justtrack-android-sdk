package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Date

internal open class DTOLogMessage(
    val level: LogLevel,
    val message: String,
    val fields: JSONObject,
    val timestamp: Date,
) : JSONEncodable {

    constructor(obj: DTOLogMessage) : this(
        level = obj.level,
        message = obj.message,
        fields = obj.fields,
        timestamp = obj.timestamp,
    )

    @Throws(JSONException::class, ParseException::class)
    constructor(obj: JSONObject, formatter: Formatter) : this(
        level = LogLevel.valueOf(obj.getString("level").uppercase()),
        message = obj.getString("message"),
        fields = obj.getJSONObject("fields"),
        timestamp = formatter.parseDate(obj.getString("timestamp")),
    )

    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("level", this.level.toString())
        obj.put("message", this.message)
        obj.put("fields", this.fields)
        obj.put("timestamp", formatter.formatDateMilliseconds(this.timestamp))
        return obj
    }
}
