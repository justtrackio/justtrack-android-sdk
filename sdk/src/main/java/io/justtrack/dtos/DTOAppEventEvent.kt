package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import io.justtrack.events.Unit
import org.json.JSONException
import org.json.JSONObject
import java.util.Date
import java.util.UUID

internal open class DTOAppEventEvent(
    val id: UUID,
    val name: String,
    val dimensions: JSONObject?,
    val value: Double,
    val unit: Unit?,
    val currency: String?,
    val sessionId: String,
    val happenedAt: Date,
    val sequenceNumber: Long,
) : JSONEncodable {

    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("id", this.id.toString())
        obj.put("name", this.name)

        if (dimensions != null && dimensions.length() > 0) {
            obj.put("dimensions", this.dimensions)
        }

        if (unit != null || currency != null) {
            obj.put("value", this.value)
        }

        if (unit != null) {
            obj.put("unit", this.unit.toString())
        }

        if (currency != null) {
            obj.put("currency", this.currency)
        }

        obj.put("sessionId", this.sessionId)
        obj.put("happenedAt", formatter.formatDateMilliseconds(this.happenedAt))
        obj.put("sequenceNumber", this.sequenceNumber)
        return obj
    }
}
