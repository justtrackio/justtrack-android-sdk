package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOPublishCustomUserIdRequest(
    val installId: String,
    val customUserId: String,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("installId", this.installId)
        obj.put("customUserId", this.customUserId)
        return obj
    }
}
