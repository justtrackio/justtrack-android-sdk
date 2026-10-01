package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.JSONEncodable
import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.log.Logger
import org.json.JSONObject

internal class ConfigApiImpl(
    private val httpClient: HttpClient,
    private val headerProvider: HeaderProvider,
    private val environment: Environment,
    private val logger: Logger,
) : ConfigApi {
    override suspend fun fetchRemoteConfig(
        queryParams: RemoteConfigQueryParams,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<RemoteConfigResponse> = runCatching {
        val request = createGetRequest(
            environment.getUrl(Environment.Route.REMOTE_CONFIG),
            headerProvider.provideHeaderV2(advertiserId, uuid, installId, logger),
            queryParams.toMap(),
        )

        httpClient.executeAsyncJsonRequest(
            request,
            GET_REMOTE_CONFIG_REQUEST_NAME,
            logger,
        ).map { response ->
            RemoteConfigResponse(response.body, response.headers)
        }.getOrThrow()
    }

    override suspend fun activateExperiments(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject> =
        runCatching {
            val jsonBody: JSONObject = body.toJSON(Formatter)

            val request = createPostRequest(
                environment.getUrl(Environment.Route.REMOTE_CONFIG),
                headerProvider.provideHeaderV2(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            httpClient.executeAsyncRequest(
                request,
                ACTIVATE_EXPERIMENTS,
                logger,
            ).getOrThrow()
        }

    internal companion object {
        const val GET_REMOTE_CONFIG_REQUEST_NAME = "GetRemoteConfig"
        const val ACTIVATE_EXPERIMENTS = "ActivateExperiments"
    }
}

internal interface ConfigApi {
    suspend fun fetchRemoteConfig(
        queryParams: RemoteConfigQueryParams,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<RemoteConfigResponse>
    suspend fun activateExperiments(body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<JSONObject>
}
