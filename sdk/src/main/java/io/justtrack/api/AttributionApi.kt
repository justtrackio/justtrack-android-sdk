package io.justtrack.api

import io.justtrack.Environment
import io.justtrack.Formatter
import io.justtrack.HttpClient
import io.justtrack.IPProtocol
import io.justtrack.JSONEncodable
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import io.justtrack.log.Logger
import org.json.JSONObject

internal class AttributionApiImpl(
    private val httpClient: HttpClient,
    private val headerProvider: HeaderProvider,
    private val environment: Environment,
    private val logger: Logger,
) : AttributionApi {
    override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> = runCatching {
        val headers = headerProvider.provideHeaderV2(advertiserId, null, null, logger)
        val bodyJson = body.toJSON(Formatter)
        val request = createPostRequest(
            environment.getUrl(Environment.Route.ATTRIBUTION),
            headers,
            bodyJson,
        )

        httpClient.executeAsyncRequest(
            request,
            GET_ATTRIBUTION_REQUEST_NAME,
            logger,
        ).getOrThrow()
    }

    override suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject> = runCatching {
        val headers = headerProvider.provideHeader(advertiserId, null, null, logger)
        val request = createGetRequest(
            environment.getUrl(protocol.route),
            headers,
        )
        httpClient.executeAsyncRequest(
            request,
            protocol.requestName,
            logger,
        ).getOrThrow()
    }

    override suspend fun sendCustomUserId(body: DTOPublishCustomUserIdRequest, advertiserId: String?, uuid: String, installId: String): Result<Unit> =
        runCatching {
            val jsonBody: JSONObject = body.toJSON(Formatter)

            val request = createPostRequest(
                environment.getUrl(Environment.Route.PUBLISH_CUSTOM_USER_ID),
                headerProvider.provideHeader(advertiserId, uuid, installId, logger),
                jsonBody,
            )
            httpClient.executeAsyncRequest(
                request,
                SEND_CUSTOM_USER_ID_REQUEST_NAME,
                logger,
            ).getOrThrow()
        }

    override suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit> =
        runCatching {
            val jsonBody: JSONObject =
                body.toJSON(Formatter)

            val request = createPostRequest(
                environment.getUrl(Environment.Route.PUBLISH_FIREBASE_APP_INSTANCE_ID),
                headerProvider.provideHeader(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            httpClient.executeAsyncRequest(
                request,
                SEND_FIREBASE_APP_INSTANCE_ID_REQUEST_NAME,
                logger,
            ).getOrThrow()
        }

    internal companion object {
        const val GET_ATTRIBUTION_REQUEST_NAME = "GetAttribution"
        const val SEND_FIREBASE_APP_INSTANCE_ID_REQUEST_NAME = "SendFirebaseAppInstanceId"
        const val SEND_CUSTOM_USER_ID_REQUEST_NAME = "SendCustomUserId"
    }
}

internal interface AttributionApi {
    suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?>

    suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject>

    suspend fun sendCustomUserId(body: DTOPublishCustomUserIdRequest, advertiserId: String?, uuid: String, installId: String): Result<Unit>

    suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit>
}
