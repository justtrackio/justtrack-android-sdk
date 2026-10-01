package io.justtrack.api

import io.justtrack.IPProtocol
import io.justtrack.JSONEncodable
import io.justtrack.AttributionTest
import io.justtrack.dtos.DTOPublishCustomUserIdRequest
import org.json.JSONException
import org.json.JSONObject

internal open class DefaultAttributionApi : AttributionApi {
    override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
        return Result.success(AttributionTest.testAttribution)
    }

    override suspend fun getSignedIpClaim(protocol: IPProtocol, advertiserId: String?): Result<JSONObject> {
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

    override suspend fun sendCustomUserId(body: DTOPublishCustomUserIdRequest, advertiserId: String?, uuid: String, installId: String): Result<Unit> {
        return Result.failure(Exception("Is not implemented"))
    }

    override suspend fun sendFirebaseAppInstanceId(body: JSONEncodable, advertiserId: String?, uuid: String, installId: String): Result<Unit> {
        return Result.failure(Exception("Is not implemented"))
    }
}
