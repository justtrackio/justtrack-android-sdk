package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOAnonymousUser(
    val installInstanceId: String,
    val deviceId: String?,
    val androidId: String?,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("installInstanceId", this.installInstanceId)
        obj.put("deviceId", this.deviceId ?: JSONObject.NULL)
        obj.put("androidId", this.androidId ?: JSONObject.NULL)
        return obj
    }
}
