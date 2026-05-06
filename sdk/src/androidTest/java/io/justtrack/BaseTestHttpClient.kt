package io.justtrack

import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.log.Logger
import io.justtrack.okhttp.Headers
import org.json.JSONException
import org.json.JSONObject

internal open class BaseTestHttpClient : HttpClient {

    override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
        return Result.success(JSONObject())
    }

    override suspend fun sendUserEvents(
        logger: Logger,
        body: DTOAppEvent,
        advertiserId: String?,
        uuid: String,
        installId: String,
    ): Result<JSONObject?> {
        return Result.success(JSONObject())
    }

    override suspend fun sendCustomUserId(
        logger: Logger,
        body: DTOPublishCustomUserIdRequest,
        advertiserId: String?,
        uuid: String,
        installId: String,
    ): Result<Unit> {
        return Result.failure(Exception("Is not implemented"))
    }

    override suspend fun sendFirebaseAppInstanceId(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String,
        installId: String,
    ): Result<Unit> {
        return Result.failure(Exception("Is not implemented"))
    }

    override suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getSignedIpClaim(logger: Logger, protocol: IPProtocol, advertiserId: String?): Result<JSONObject> {
        val response = JSONObject()
        try {
            response.put("ip", "some ip")
            response.put("type", protocol.name)
            response.put("token", "some token")
        } catch (exception: JSONException) {
            return Result.failure(exception)
        }
        return Result.success(response)
    }

    override fun setUserEventRules(rules: List<DTOAttributionOutputSdkRule>) {
        // just ignore it
    }

    override suspend fun reportIntegrity(logger: Logger, body: JSONEncodable, installId: String): Result<JSONObject> {
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

    override suspend fun anonymizeUser(
        logger: Logger,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
        body: JSONEncodable,
    ): Result<JSONObject> {
        return Result.success(JSONObject())
    }

    override suspend fun setExperimentVariant(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<JSONObject> {
        return Result.success(JSONObject())
    }

    override suspend fun fetchRemoteConfig(
        logger: Logger,
        queryParams: RemoteConfigQueryParams,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<RemoteConfigResponse> {
        return Result.success(RemoteConfigResponse(JSONObject(), Headers.headersOf()))
    }

    override suspend fun activateExperiments(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<JSONObject> {
        return Result.success(JSONObject())
    }
}
