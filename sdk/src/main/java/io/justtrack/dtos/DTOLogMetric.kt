package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Date

internal open class DTOLogMetric(
    val metric: String,
    val dimensions: JSONObject,
    val value: Double,
    val unit: String,
    val timestamp: Date,
) : JSONEncodable {
    @Throws(JSONException::class, ParseException::class)
    constructor(obj: JSONObject, formatter: Formatter) : this(
        metric = obj.getString("metric"),
        dimensions = obj.getJSONObject("dimensions"),
        value = obj.getDouble("value"),
        unit = obj.getString("unit"),
        timestamp = formatter.parseDate(obj.getString("timestamp")),
    )

    constructor(obj: DTOLogMetric) : this(
        metric = obj.metric,
        dimensions = obj.dimensions,
        value = obj.value,
        unit = obj.unit,
        timestamp = obj.timestamp,
    )

    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("metric", this.metric)
        obj.put("dimensions", this.dimensions)
        obj.put("value", this.value)
        obj.put("unit", this.unit)
        obj.put("timestamp", formatter.formatDateMilliseconds(this.timestamp))
        return obj
    }
}
