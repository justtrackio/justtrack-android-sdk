package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

internal data class DTOSetExperimentVariant(
    val installInstanceId: String,
    val justtrackSdkVersion: String,
    val appVersionName: String,
    val appVersionCode: String,
    val osVersion: String,
    val experiment: String,
    val variant: String,
    val tags: List<String>?,
    val happenedAt: Date?,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("installInstanceId", this.installInstanceId)
        obj.put("justtrackSdkVersion", this.justtrackSdkVersion)
        obj.put("appVersionName", this.appVersionName)
        obj.put("appVersionCode", this.appVersionCode)
        obj.put("osVersion", this.osVersion)
        obj.put("experiment", this.experiment)
        obj.put("variant", this.variant)

        if (tags != null) {
            val tagsJsonArray = JSONArray()
            for (data in tags) {
                tagsJsonArray.put(data)
            }
            obj.put("tags", tagsJsonArray)
        } else {
            obj.put("tags", JSONObject.NULL)
        }

        obj.put("happenedAt", this.happenedAt?.let { formatter.formatDateMilliseconds(it) } ?: JSONObject.NULL)
        return obj
    }
}
