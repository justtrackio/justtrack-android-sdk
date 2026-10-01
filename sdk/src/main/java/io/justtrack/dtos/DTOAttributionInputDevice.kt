package io.justtrack.dtos

import io.justtrack.DeviceType
import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionInputDevice(
    val name: String,
    val model: String,
    val product: String,
    val type: DeviceType,
    val os: DTOAttributionInputDeviceOS,
    val display: DTOAttributionInputDeviceDisplay,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("name", this.name)
        obj.put("model", this.model)
        obj.put("product", this.product)
        obj.put("type", this.type.toString())
        obj.put("os", this.os.toJSON(formatter))
        obj.put("display", this.display.toJSON(formatter))
        return obj
    }
}
