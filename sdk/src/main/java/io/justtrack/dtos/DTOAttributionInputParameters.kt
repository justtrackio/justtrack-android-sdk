package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOAttributionInputParameters(
    val installSource: String?,
    val integritySecret: String?,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("installSource", this.installSource ?: JSONObject.NULL)
        obj.put("integritySecret", this.integritySecret ?: JSONObject.NULL)
        return obj
    }
}
