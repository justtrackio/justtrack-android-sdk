package io.justtrack.dtos

import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionOutputRetargeting(
    val url: String,
    val attributes: Map<String, String>,
) {
    @Throws(JSONException::class)
    constructor(obj: JSONObject) : this(
        url = obj.getString("url"),
        attributes = buildMap {
            val attributesJsonObject = obj.getJSONObject("attributes")
            val keys = attributesJsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, attributesJsonObject.getString(key))
            }
        },
    )
}
