package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class DTOAppEvent(
    val appVersion: DTOAppVersion,
    val sdkVersion: DTOSdkVersion,
    val user: DTOAppEventUser,
    val device: DTOAppEventDevice,
    val events: List<DTOAppEventEvent>,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("appVersion", this.appVersion.toJSON(formatter))
        obj.put("sdkVersion", this.sdkVersion.toJSON(formatter))
        obj.put("user", this.user.toJSON(formatter))
        obj.put("device", this.device.toJSON(formatter))
        val eventsJsonArray = JSONArray()
        for (data in events) {
            eventsJsonArray.put(data.toJSON(formatter))
        }
        obj.put("events", eventsJsonArray)
        return obj
    }
}
