package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionInputUser(
    val userId: String?,
    val customUserId: String?,
    val installInstanceId: String,
    val deviceId: String,
    val advertiserId: String?,
    val trackingId: String?,
    val trackingProvider: String,
    val countryIso: String?,
    val appSetId: String?,
    val hasLimitedAdTracking: Boolean,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("userId", this.userId ?: JSONObject.NULL)
        obj.put("customUserId", this.customUserId ?: JSONObject.NULL)
        obj.put("installInstanceId", this.installInstanceId)
        obj.put("deviceId", this.deviceId)
        obj.put("advertiserId", this.advertiserId ?: JSONObject.NULL)
        obj.put("trackingId", this.trackingId ?: JSONObject.NULL)
        obj.put("trackingProvider", this.trackingProvider)
        obj.put("countryIso", this.countryIso ?: JSONObject.NULL)
        obj.put("appSetId", this.appSetId ?: JSONObject.NULL)
        obj.put("hasLimitedAdTracking", this.hasLimitedAdTracking)
        return obj
    }
}
