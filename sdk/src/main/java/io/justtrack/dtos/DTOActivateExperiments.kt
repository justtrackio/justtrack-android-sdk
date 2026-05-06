package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class DTOActivateExperiments(val installInstanceId: String, val experimentIds: List<String>) :
    JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("installInstanceId", this.installInstanceId)
        val experimentIdsJsonArray = JSONArray()
        for (data in experimentIds) {
            experimentIdsJsonArray.put(data)
        }
        obj.put("experimentIds", experimentIdsJsonArray)
        return obj
    }
}
