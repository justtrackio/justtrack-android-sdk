package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal data class DTOSdkVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val name: String,
    val platform: String,
    val wrapper: String?,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("major", this.major)
        obj.put("minor", this.minor)
        obj.put("patch", this.patch)
        obj.put("name", this.name)
        obj.put("platform", this.platform)
        obj.put("wrapper", this.wrapper ?: JSONObject.NULL)
        return obj
    }
}
