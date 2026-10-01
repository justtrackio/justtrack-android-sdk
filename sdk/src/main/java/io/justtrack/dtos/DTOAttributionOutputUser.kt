package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionOutputUser(
    val installId: String,
    val type: String,
    val redownload: Boolean,
) {
    @Throws(JSONException::class)
    constructor(obj: JSONObject) : this(
        installId = obj.getString("installId"),
        type = obj.getString("type"),
        redownload = obj.getBoolean("redownload"),
    )
}
