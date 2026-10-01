package io.justtrack.api

import io.justtrack.JSONEncodable
import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.okhttp.Headers
import org.json.JSONObject

internal open class DefaultConfigApi : ConfigApi {
    override suspend fun fetchRemoteConfig(
        queryParams: RemoteConfigQueryParams,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<RemoteConfigResponse> {
        return Result.success(RemoteConfigResponse(JSONObject(), Headers.headersOf()))
    }

    override suspend fun activateExperiments(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject> {
        return Result.success(JSONObject())
    }
}
