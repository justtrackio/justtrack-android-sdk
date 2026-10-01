package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionInput(
    val appVersion: DTOAppVersion,
    val sdkVersion: DTOSdkVersion,
    val user: DTOAttributionInputUser,
    val device: DTOAttributionInputDevice,
    val claims: Iterable<String>,
    val parameters: DTOAttributionInputParameters,
    val referrer: DTOAttributionInputReferrer?,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("appVersion", this.appVersion.toJSON(formatter))
        obj.put("sdkVersion", this.sdkVersion.toJSON(formatter))
        obj.put("user", this.user.toJSON(formatter))
        obj.put("device", this.device.toJSON(formatter))
        val claimsJsonArray = JSONArray()
        for (data in claims) {
            claimsJsonArray.put(data)
        }
        obj.put("claims", claimsJsonArray)
        obj.put("parameters", this.parameters.toJSON(formatter))
        obj.put("referrer", this.referrer?.toJSON(formatter) ?: JSONObject.NULL)
        return obj
    }
}
