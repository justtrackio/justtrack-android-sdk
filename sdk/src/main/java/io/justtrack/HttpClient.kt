package io.justtrack

import io.justtrack.log.Logger
import io.justtrack.okhttp.Headers
import io.justtrack.okhttp.Request
import org.json.JSONObject

internal interface HttpClient {
    suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject>

    suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<JsonHttpResponse>

    data class JsonHttpResponse(
        val body: JSONObject,
        val headers: Headers?,
    )
}
