package io.justtrack

import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.log.Logger
import org.json.JSONObject

internal interface HttpClient {

    suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?>

    suspend fun sendUserEvents(logger: Logger, body: DTOAppEvent, advertiserId: String?, uuid: String, installId: String): Result<JSONObject?>

    suspend fun sendCustomUserId(
        logger: Logger,
        body: DTOPublishCustomUserIdRequest,
        advertiserId: String?,
        uuid: String,
        installId: String,
    ): Result<Unit>

    suspend fun sendFirebaseAppInstanceId(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit>

    suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit>

    suspend fun getSignedIpClaim(logger: Logger, protocol: IPProtocol, advertiserId: String?): Result<JSONObject>

    fun setUserEventRules(rules: List<DTOAttributionOutputSdkRule>)

    suspend fun reportIntegrity(logger: Logger, body: JSONEncodable, installId: String): Result<JSONObject>

    suspend fun anonymizeUser(logger: Logger, advertiserId: String?, uuid: String?, installId: String?, body: JSONEncodable): Result<JSONObject>

    suspend fun setExperimentVariant(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<JSONObject>

    suspend fun fetchRemoteConfig(
        logger: Logger,
        queryParams: RemoteConfigQueryParams,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<RemoteConfigResponse>

    suspend fun activateExperiments(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<JSONObject>
}
