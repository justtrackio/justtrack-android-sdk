package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

internal data class DTOAppEventUser(
    val deviceId: String?,
    val countryIso: String?,
    val localeCode: String?,
    val userId: UUID,
    val installInstanceId: UUID,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("deviceId", this.deviceId ?: JSONObject.NULL)
        obj.put("countryIso", this.countryIso ?: JSONObject.NULL)
        obj.put("localeCode", this.localeCode ?: JSONObject.NULL)
        obj.put("userId", this.userId.toString())
        obj.put("installInstanceId", this.installInstanceId.toString())
        return obj
    }
}
