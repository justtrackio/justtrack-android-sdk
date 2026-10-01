package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOPublishFirebaseAppInstanceIdRequest(
    val uuid: String,
    val firebaseInstanceId: String,
) : JSONEncodable {

    @Throws(JSONException::class)
    constructor(obj: JSONObject) : this(
        uuid = obj.getString("uuid"),
        firebaseInstanceId = obj.getString("firebaseInstanceId"),
    )

    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("uuid", this.uuid)
        obj.put("firebaseInstanceId", this.firebaseInstanceId)
        return obj
    }
}
