package io.justtrack

import androidx.annotation.VisibleForTesting
import org.json.JSONObject

internal class SdkConfigTask internal constructor(
    private val taskExecutor: TaskExecutor,
    val databaseInterface: DatabaseInterface,
    val logger: HttpLogger,
    val httpClient: HttpClient,
) {

    init {
        applyRules()
    }

    private fun applyRules() {
        taskExecutor.executeAsFuture(
            ApplyingConfigTask(
                databaseInterface,
                logger,
                httpClient,
            ),
        )
    }

    internal class ApplyingConfigTask internal constructor(
        val databaseInterface: DatabaseInterface,
        val logger: HttpLogger,
        val httpClient: HttpClient,
    ) : Task<Unit> {
        override suspend fun execute() {
            val storedSdkConfig = fetchStoredSDKConfig()
            if (storedSdkConfig != null) {
                logger.setLogAndMetricRules(storedSdkConfig)
                httpClient.setUserEventRules(storedSdkConfig.event.rules)
            }
        }

        @VisibleForTesting
        internal suspend fun fetchStoredSDKConfig(): DTOAttributionOutputSdkConfig? {
            try {
                var sdkConfigString: String?
                databaseInterface.openAttribution().use {
                    sdkConfigString = it.getSdkConfig()
                }

                val sdkConfig = sdkConfigString ?: return null

                return DTOAttributionOutputSdkConfig(JSONObject(sdkConfig))
            } catch (exception: Throwable) {
                logger.warn("Failed to parse stored SDK config", exception)

                return null
            }
        }
    }
}
