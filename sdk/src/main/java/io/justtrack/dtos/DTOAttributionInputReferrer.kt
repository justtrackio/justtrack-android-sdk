package io.justtrack.dtos

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

internal data class DTOAttributionInputReferrer(
    val value: String,
    val clickDate: Date,
    val installBeginDate: Date,
    val clientDate: Date,
    val serverClickDate: Date,
    val serverInstallBeginDate: Date,
    val installVersion: String?,
) : JSONEncodable {
    @Throws(JSONException::class)
    override fun toJSON(formatter: Formatter): JSONObject {
        val obj = JSONObject()
        obj.put("value", this.value)
        obj.put("clickDate", formatter.formatDateMilliseconds(this.clickDate))
        obj.put("installBeginDate", formatter.formatDateMilliseconds(this.installBeginDate))
        obj.put("clientDate", formatter.formatDateMilliseconds(this.clientDate))
        obj.put("serverClickDate", formatter.formatDateMilliseconds(this.serverClickDate))
        obj.put("serverInstallBeginDate", formatter.formatDateMilliseconds(this.serverInstallBeginDate))
        obj.put("installVersion", this.installVersion ?: JSONObject.NULL)
        return obj
    }
}
