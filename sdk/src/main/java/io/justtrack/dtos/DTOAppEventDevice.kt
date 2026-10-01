package io.justtrack.dtos

import io.justtrack.ConnectionType
import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

internal data class DTOAppEventDevice(
    val connectionType: ConnectionType,
    val os: DTOAppEventDeviceOS,
    val date: Date,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("connectionType", this.connectionType.toString())
        obj.put("os", this.os.toJSON(formatter))
        obj.put("date", formatter.formatDateMilliseconds(this.date))
        return obj
    }
}
