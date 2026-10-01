package io.justtrack.dtos

import io.justtrack.Formatter
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.util.Date

internal data class DTOAttributionOutputAttribution(
    val campaign: DTOAttributionOutputAttributionCampaign,
    val channel: DTOAttributionOutputAttributionChannel,
    val network: DTOAttributionOutputAttributionNetwork,
    val sourceId: String?,
    val sourceBundleId: String?,
    val sourcePlacement: String?,
    val adsetId: String?,
    val attributedAt: Date,
) {
    @Throws(JSONException::class, ParseException::class)
    constructor(obj: JSONObject, formatter: Formatter) : this(
        campaign = DTOAttributionOutputAttributionCampaign(obj.getJSONObject("campaign")),
        channel = DTOAttributionOutputAttributionChannel(obj.getJSONObject("channel")),
        network = DTOAttributionOutputAttributionNetwork(obj.getJSONObject("network")),
        sourceId = if (obj.has("sourceId") && obj.get("sourceId") != JSONObject.NULL) {
            obj.getString("sourceId")
        } else {
            null
        },
        sourceBundleId = if (obj.has("sourceBundleId") && obj.get("sourceBundleId") != JSONObject.NULL) {
            obj.getString("sourceBundleId")
        } else {
            null
        },
        sourcePlacement = if (obj.has("sourcePlacement") && obj.get("sourcePlacement") != JSONObject.NULL) {
            obj.getString("sourcePlacement")
        } else {
            null
        },
        adsetId = if (obj.has("adsetId") && obj.get("adsetId") != JSONObject.NULL) obj.getString("adsetId") else null,
        attributedAt = formatter.parseDate(obj.getString("attributedAt")),
    )
}
