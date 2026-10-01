package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionOutputAttributionChannel(
    val id: Int,
    val name: String,
    val incent: Boolean,
) {
    @Throws(JSONException::class)
    constructor(obj: JSONObject) : this(
        id = obj.getInt("id"),
        name = obj.getString("name"),
        incent = obj.getBoolean("incent"),
    )
}
