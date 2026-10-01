package io.justtrack.api

import io.justtrack.dtos.DTOAppEvent
import org.json.JSONObject

internal open class DefaultEventApi : EventApi {
    override suspend fun sendUserEvents(body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?> {
        return Result.success(JSONObject())
    }
}
