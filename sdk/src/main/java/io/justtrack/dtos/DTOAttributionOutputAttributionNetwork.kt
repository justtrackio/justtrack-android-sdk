package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionOutputAttributionNetwork(
    val id: Int,
    val name: String,
) {
    @Throws(JSONException::class)
    constructor(obj: JSONObject) : this(
        id = obj.getInt("id"),
        name = obj.getString("name"),
    )
}
