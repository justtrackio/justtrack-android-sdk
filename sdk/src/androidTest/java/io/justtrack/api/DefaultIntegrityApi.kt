package io.justtrack.api

import io.justtrack.Formatter
import io.justtrack.JSONEncodable
import org.json.JSONException
import org.json.JSONObject

internal open class DefaultIntegrityApi : IntegrityApi {
    override suspend fun reportIntegrity(body: JSONEncodable, installId: String): Result<JSONObject> {
        return try {
            if (body.toJSON(Formatter).getString("token").isEmpty()) {
                Result.failure(Exception("No token"))
            } else {
                Result.success(JSONObject())
            }
        } catch (exception: JSONException) {
            Result.failure(exception)
        }
    }
}
