package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException

internal data class DTOAttributionOutput(
    val user: DTOAttributionOutputUser,
    val attribution: DTOAttributionOutputAttribution,
    val retargeting: DTOAttributionOutputRetargeting?,
) {
    @Throws(JSONException::class, ParseException::class)
    constructor(obj: JSONObject, formatter: Formatter) : this(
        user = DTOAttributionOutputUser(obj.getJSONObject("user")),
        attribution = DTOAttributionOutputAttribution(obj.getJSONObject("attribution"), formatter),
        retargeting = if (obj.has("retargeting") && obj.get("retargeting") != JSONObject.NULL) {
            DTOAttributionOutputRetargeting(obj.getJSONObject("retargeting"))
        } else {
            null
        },
    )
}
