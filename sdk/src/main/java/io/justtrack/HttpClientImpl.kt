package io.justtrack

import io.justtrack.config.RemoteConfigQueryParams
import io.justtrack.config.RemoteConfigResponse
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.log.LoggerFieldsBuilder
import io.justtrack.okhttp.Headers
import io.justtrack.okhttp.HttpUrl.Companion.toHttpUrlOrNull
import io.justtrack.okhttp.Request
import io.justtrack.okhttp.RequestBody.Companion.toRequestBody
import io.justtrack.okhttp.Response
import io.justtrack.versions.VersionBundle
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

internal class HttpClientImpl internal constructor(
    private val deviceInfo: DeviceInfo,
    private val apiToken: String,
    private val environment: Environment,
    private val applicationPackageName: String,
    private val versionBundle: VersionBundle,
    consoleLogger: Logger?,
) : HttpClient {
    private val client: OkHttpClientExecutor = OkHttpClientExecutor(
        consoleLogger = consoleLogger,
    )

    private var userAgent: String? = null
    private var useEventRules: List<DTOAttributionOutputSdkRule>? = null

    override suspend fun sendAttributionRequest(logger: Logger, body: JSONEncodable, advertiserId: String?): Result<JSONObject?> {
        val headers = getHeadersV2(advertiserId, null, null, logger)
        return try {
            val bodyJson = body.toJSON(Formatter)
            val request = createPostRequest(
                environment.getUrl(Environment.Route.ATTRIBUTION),
                headers,
                bodyJson,
            )
            executeAsyncRequest(
                request,
                GET_ATTRIBUTION_REQUEST_NAME,
                logger,
            )
        } catch (exception: JSONException) {
            Result.failure(exception)
        }
    }

    override suspend fun sendUserEvents(
        logger: Logger,
        body: DTOAppEvent,
        advertiserId: String?,
        uuid: String,
        installId: String,
    ): Result<JSONObject?> {
        return try {
            val bodyReduced = EventLimiter.filterEvents(
                body,
                useEventRules,
                if (logger is HttpLogger) logger.fallback else logger,
            )

            if (bodyReduced == null) {
                Result.success(null)
            } else {
                val jsonBody: JSONObject? = try {
                    bodyReduced.toJSON(Formatter)
                } catch (e: JSONException) {
                    return Result.failure(e)
                }
                val request = createPostRequest(
                    environment.getUrl(Environment.Route.TRACK_EVENT),
                    getHeadersV2(advertiserId, uuid, installId, logger),
                    jsonBody,
                )
                executeAsyncRequest(
                    request,
                    SEND_USER_EVENTS_REQUEST_NAME,
                    logger,
                )
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun sendCustomUserId(
        logger: Logger,
        body: DTOPublishCustomUserIdRequest,
        advertiserId: String?,
        uuid: String,
        installId: String,
    ): Result<Unit> {
        return try {
            val jsonBody: JSONObject = try {
                body.toJSON(Formatter)
            } catch (e: JSONException) {
                return Result.failure(e)
            }

            val request = createPostRequest(
                environment.getUrl(Environment.Route.PUBLISH_CUSTOM_USER_ID),
                getHeaders(advertiserId, uuid, installId, logger),
                jsonBody,
            )
            executeAsyncRequest(
                request,
                SEND_CUSTOM_USER_ID_REQUEST_NAME,
                logger,
            ).map { Unit }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun sendFirebaseAppInstanceId(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String,
        installId: String,
    ): Result<Unit> {
        return try {
            val jsonBody: JSONObject = try {
                body.toJSON(Formatter)
            } catch (e: JSONException) {
                return Result.failure(e)
            }

            val request = createPostRequest(
                environment.getUrl(Environment.Route.PUBLISH_FIREBASE_APP_INSTANCE_ID),
                getHeaders(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            executeAsyncRequest(
                request,
                SEND_FIREBASE_APP_INSTANCE_ID_REQUEST_NAME,
                logger,
            ).map { Unit }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit> {
        return try {
            val jsonBody: JSONObject = try {
                body.toJSON(Formatter)
            } catch (e: JSONException) {
                return Result.failure(e)
            }

            val request = createPostRequest(
                environment.getUrl(Environment.Route.LOG),
                getHeaders(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            executeAsyncRequest(
                request,
                SEND_LOGS_REQUEST_NAME,
                logger,
            ).map { Unit }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun getSignedIpClaim(logger: Logger, protocol: IPProtocol, advertiserId: String?) = try {
        val headers = getHeaders(advertiserId, null, null, logger)
        val request = createGetRequest(
            environment.getUrl(protocol.route),
            headers,
        )
        executeAsyncRequest(
            request,
            protocol.requestName,
            logger,
        )
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    override fun setUserEventRules(rules: List<DTOAttributionOutputSdkRule>) {
        this.useEventRules = rules
    }

    override suspend fun reportIntegrity(logger: Logger, body: JSONEncodable, installId: String): Result<JSONObject> {
        return try {
            val jsonBody: JSONObject = try {
                body.toJSON(Formatter)
            } catch (e: JSONException) {
                return Result.failure(e)
            }

            val request = createPostRequest(
                environment.getUrl(Environment.Route.REPORT_INTEGRITY),
                getHeaders(null, null, installId, logger),
                jsonBody,
            )

            executeAsyncRequest(
                request,
                SEND_INTEGRITY_TOKEN_NAME,
                logger,
            )
        } catch (exception: Exception) {
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
        return try {
            val jsonBody: JSONObject = try {
                body.toJSON(Formatter)
            } catch (e: JSONException) {
                return Result.failure(e)
            }

            val request = createPostRequest(
                environment.getUrl(Environment.Route.ANONYMIZE),
                getHeadersV2(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            executeAsyncRequest(
                request,
                SEND_ANONYMIZE_REQUEST_NAME,
                logger,
            )
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun setExperimentVariant(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<JSONObject> {
        return try {
            val jsonBody: JSONObject = try {
                body.toJSON(Formatter)
            } catch (e: JSONException) {
                return Result.failure(e)
            }

            val request = createPostRequest(
                environment.getUrl(Environment.Route.AB_TEST_ASSIGNMENT),
                getHeadersV2(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            executeAsyncRequest(
                request,
                SET_EXPERIMENT_VARIANT_REQUEST_NAME,
                logger,
            )
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun fetchRemoteConfig(
        logger: Logger,
        queryParams: RemoteConfigQueryParams,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<RemoteConfigResponse> {
        return try {
            val request = createGetRequest(
                environment.getUrl(Environment.Route.REMOTE_CONFIG),
                getHeadersV2(advertiserId, uuid, installId, logger),
                queryParams.toMap(),
            )

            executeAsyncJsonRequest(
                request,
                GET_REMOTE_CONFIG_REQUEST_NAME,
                logger,
            ).map { response ->
                RemoteConfigResponse(response.body, response.headers)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun activateExperiments(
        logger: Logger,
        body: JSONEncodable,
        advertiserId: String?,
        uuid: String?,
        installId: String?,
    ): Result<JSONObject> {
        return try {
            val jsonBody: JSONObject = try {
                body.toJSON(Formatter)
            } catch (e: JSONException) {
                return Result.failure(e)
            }

            val request = createPostRequest(
                environment.getUrl(Environment.Route.REMOTE_CONFIG),
                getHeadersV2(advertiserId, uuid, installId, logger),
                jsonBody,
            )

            executeAsyncRequest(
                request,
                ACTIVATE_EXPERIMENTS,
                logger,
            )
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun getHeaders(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers {
        return Headers.Builder().apply {
            this.add("X-CLIENT-ID", applicationPackageName)
            this.add("X-CLIENT-TOKEN", apiToken)
            this.add("X-ADVERTISER-ID", advertiserId ?: "missing")

            // Accept-Encoding is automatically added by default. Do not add them manually. Content-Encoding also added in interceptor.

            if (uuid != null) {
                this.add("X-USER-ID", uuid)
            }
            if (installId != null) {
                this.add("X-INSTALL-ID", installId)
            }
            this.add("User-Agent", getUserAgent(logger))
        }.build()
    }

    private fun getHeadersV2(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers {
        return Headers.Builder().apply {
            this.add("X-APP-BUNDLE-ID", applicationPackageName)
            this.add("X-APP-TOKEN", apiToken)
            this.add("X-ADVERTISER-ID", advertiserId ?: "missing")

            // Accept-Encoding is automatically added by default. Do not add them manually. Content-Encoding also added in interceptor.

            if (uuid != null) {
                this.add("X-USER-ID", uuid)
            }
            if (installId != null) {
                this.add("X-INSTALL-ID", installId)
            }
            this.add("User-Agent", getUserAgent(logger))
        }.build()
    }

    // Deadlock-Safety: This doesn't take any additional locks and just updates the userAgent string.
    @Synchronized
    private fun getUserAgent(logger: Logger): String {
        userAgent?.let {
            return it
        }

        val sdkVersionName = versionBundle.sdkVersion.name
        val product = deviceInfo.deviceProduct
        val device = deviceInfo.deviceModel
        val cpu = deviceInfo.cpuArch
        val osVersion = deviceInfo.osVersion
        val locale = Locale.getDefault().toString()
        val build = deviceInfo.build
        var appName = deviceInfo.getAppName()
        if (appName != null) {
            appName = appName.replace("\\W".toRegex(), "_")
        }

        val appVersionName = versionBundle.applicationVersion.getVersionName().ifEmpty {
            versionBundle.applicationVersion.getVersionCode()
        }
        val platformType = versionBundle.sdkVersion.platformType.toString()
        // be careful with the format - the backend parses this to extract some information
        val userAgent = "JustTrackSDK/" + sdkVersionName +
            " (" + product + "; " + device + "; " + (if (cpu != null) "$cpu CPU; " else "") +
            "Android " + osVersion + "; " + locale + "; Build/" + build + ") " +
            (if (appName != null) "$appName/$appVersionName ($platformType)" else "")

        this.userAgent = userAgent

        logger.debug(
            "Initialized user agent",
            LoggerFieldsBuilder().with(
                "userAgent",
                userAgent,
            ),
        )
        return userAgent
    }

    private suspend fun executeAsyncRequest(request: Request, requestName: String, logger: Logger): Result<JSONObject> {
        return executeAsyncJsonRequest(request, requestName, logger).map { it.body }
    }

    private suspend fun executeAsyncJsonRequest(request: Request, requestName: String, logger: Logger): Result<JsonHttpResponse> {
        val connectionType = deviceInfo.getConnectionType()

        return client.sendRequest(request).fold(
            onSuccess = { response ->
                onExecuteSuccess(response)
            },
            onFailure = { e ->
                val dimensions: LoggerFields =
                    LoggerFieldsBuilder()
                        .with("Request", requestName)
                        .with("Network", connectionType.toString())
                        .with("Reason", "NetworkProblem")
                logger.publishMetric(REQUEST_FAILURES_METRIC, 1.0, dimensions)
                Result.failure(NetworkProblemException(e))
            },
        )
    }

    private fun onExecuteSuccess(response: Response): Result<JsonHttpResponse> {
        return try {
            val responseData = response.body?.string()
            when {
                !response.isSuccessful ->
                    Result.failure(
                        BadResponseException(
                            BadResponseException.formatBadResponseStatusMessage(
                                response.code,
                                response.message,
                                responseData,
                            ),
                            response.code,
                            responseData,
                        ),
                    )

                responseData == null ->
                    Result.failure(
                        BadResponseException(
                            "No response body was returned",
                            response.code,
                        ),
                    )

                else -> {
                    try {
                        Result.success(
                            JsonHttpResponse(
                                JSONObject(responseData),
                                response.headers,
                            ),
                        )
                    } catch (e: JSONException) {
                        Result.failure(
                            BadResponseException(
                                "Failed to parse response body as JSON",
                                response.code,
                                responseData,
                                e,
                            ),
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Result.failure(RuntimeException("Failed to handle response", e))
        }
    }

    private fun createPostRequest(url: String, headers: Headers, bodyJson: JSONObject?): Request {
        return Request.Builder().apply {
            this.url(url)
            this.headers(headers)

            val responseBody = bodyJson?.toString()?.toRequestBody() ?: ByteArray(0).toRequestBody()
            this.post(responseBody)
        }.build()
    }

    private fun createGetRequest(url: String, headers: Headers, params: Map<String, String> = emptyMap()): Request {
        val baseUrl = url.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid url: $url")
        val requestUrl = if (params.isEmpty()) {
            baseUrl
        } else {
            val builder = baseUrl.newBuilder()
            for ((key, value) in params) {
                builder.addQueryParameter(key, value)
            }
            builder.build()
        }

        return Request.Builder().apply {
            this.url(requestUrl)
            this.headers(headers)
            this.get()
        }.build()
    }

    private data class JsonHttpResponse(
        val body: JSONObject,
        val headers: Headers?,
    )

    companion object {
        const val GET_ATTRIBUTION_REQUEST_NAME = "GetAttribution"
        const val SEND_USER_EVENTS_REQUEST_NAME = "SendUserEvents"
        const val SEND_CUSTOM_USER_ID_REQUEST_NAME = "SendCustomUserId"
        const val SEND_FIREBASE_APP_INSTANCE_ID_REQUEST_NAME = "SendFirebaseAppInstanceId"
        const val SEND_INTEGRITY_TOKEN_NAME = "SendIntegrityToken"
        const val SEND_ANONYMIZE_REQUEST_NAME = "SendAnonymize"
        const val SET_EXPERIMENT_VARIANT_REQUEST_NAME = "SendTestGroup"
        const val GET_REMOTE_CONFIG_REQUEST_NAME = "GetRemoteConfig"
        const val ACTIVATE_EXPERIMENTS = "ActivateExperiments"
        const val SEND_LOGS_REQUEST_NAME = "SendLogs"
        const val SIGN_IPV4_REQUEST_NAME = "SignIPv4"
        const val SIGN_IPV6_REQUEST_NAME = "SignIPv6"
        private val REQUEST_FAILURES_METRIC = Metric("RequestFailures")
    }
}
