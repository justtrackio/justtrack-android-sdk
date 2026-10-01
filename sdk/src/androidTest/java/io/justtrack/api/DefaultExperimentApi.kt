package io.justtrack.api

import io.justtrack.JSONEncodable
import org.json.JSONObject

internal open class DefaultExperimentApi : ExperimentApi {
    override suspend fun setExperimentVariant(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject> {
        return Result.success(JSONObject())
    }
}
