package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionOutputAttributionCampaign(
    val externalId: String,
    val name: String,
    val type: String,
    val organic: Boolean,
) {
    @Throws(JSONException::class)
    constructor(obj: JSONObject) : this(
        externalId = if (!obj.has("externalId") || obj.isNull("externalId")) {
            throw JSONException("Required field 'externalId' is missing or null")
        } else {
            obj.getString("externalId")
        },
        name = obj.getString("name"),
        type = obj.getString("type"),
        organic = obj.getBoolean("organic"),
    )
}
