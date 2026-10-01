package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject

internal data class DTOSignIPResponse(
    val ip: String,
    val type: String,
    val token: String,
) {
    @Throws(JSONException::class)
    internal constructor(obj: JSONObject) : this(
        ip = obj.getString("ip"),
        type = obj.getString("type"),
        token = obj.getString("token"),
    )
}
