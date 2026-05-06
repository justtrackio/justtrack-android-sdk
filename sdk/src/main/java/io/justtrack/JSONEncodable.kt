package io.justtrack

import org.json.JSONException
import org.json.JSONObject

internal interface JSONEncodable {
    @Throws(JSONException::class)
    fun toJSON(formatter: Formatter): JSONObject
}
