package io.justtrack.api

import io.justtrack.JSONEncodable
import org.json.JSONObject

internal open class DefaultPrivacyApi : PrivacyApi {
    override suspend fun anonymizeUser(advertiserId: String?, uuid: String?, installId: String?, body: JSONEncodable): Result<JSONObject> {
        return Result.success(JSONObject())
    }
}
