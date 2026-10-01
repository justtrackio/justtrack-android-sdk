package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOIntegrityToken(
    val integrityToken: String?,
    val installInstanceId: String,
    val errorCode: Int?,
    val errorMessage: String?,
) : JSONEncodable {

    @Throws(JSONException::class)
    constructor(obj: JSONObject) : this(
        integrityToken = if (obj.has("integrityToken") && obj.get("integrityToken") != JSONObject.NULL) {
            obj.getString("integrityToken")
        } else {
            null
        },
        installInstanceId = obj.getString("installInstanceId"),
        errorCode = if (obj.has("errorCode") && obj.get("errorCode") != JSONObject.NULL) {
            obj.getInt("errorCode")
        } else {
            null
        },
        errorMessage = if (obj.has("errorMessage") && obj.get("errorMessage") != JSONObject.NULL) {
            obj.getString("errorMessage")
        } else {
            null
        },
    )

    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("integrityToken", this.integrityToken ?: JSONObject.NULL)
        obj.put("installInstanceId", this.installInstanceId)
        obj.put("errorCode", this.errorCode ?: JSONObject.NULL)
        obj.put("errorMessage", this.errorMessage ?: JSONObject.NULL)
        return obj
    }
}
