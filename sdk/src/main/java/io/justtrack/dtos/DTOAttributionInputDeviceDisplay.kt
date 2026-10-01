package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionInputDeviceDisplay(
    val width: Int,
    val height: Int,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("width", this.width)
        obj.put("height", this.height)
        return obj
    }
}
