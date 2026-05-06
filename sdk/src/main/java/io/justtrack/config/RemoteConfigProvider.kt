package io.justtrack.config

import io.justtrack.AsyncFuture
import io.justtrack.FixedRetryingTask
import io.justtrack.HttpClient
import io.justtrack.Task
import io.justtrack.TaskExecutor
import io.justtrack.TrackingEventErrorClassifier
import io.justtrack.ValueFuture
import io.justtrack.log.Logger
import org.json.JSONArray
import org.json.JSONObject

internal class RemoteConfigProvider internal constructor(
    private val remoteConfigStore: RemoteConfigStore,
    private val taskExecutor: TaskExecutor,
    private val httpClient: HttpClient,
    private val attributionParams: RemoteConfigImpl.AttributionParams,
    private val remoteConfigTimeStamp: RemoteConfigTimestamp,
    private val logger: Logger,
    private val retryConfig: List<Int>,
) {
    private var cacheFuture: AsyncFuture<Void?>? = null

    private var currentFetchInterval: Long = remoteConfigStore.getCurrentFetchInterval()
    private var previousFetchTimeStamp: Long? = remoteConfigStore.getPreviousFetchTimeStamp()

    fun fetch(): AsyncFuture<Void?> {
        val retryAfterSeconds = remoteConfigStore.getRetryAfterSeconds()
        val effectiveInterval = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            remoteConfigStore.setRetryAfterSeconds(RemoteConfigStoreImpl.CONSUMED_RETRY_AFTER)
            minOf(currentFetchInterval, retryAfterSeconds.toLong())
        } else {
            currentFetchInterval
        }
        val isWithinInterval = isWithinInterval(previousFetchTimeStamp, effectiveInterval)
        val storedConfig = remoteConfigStore.getStoredAssignments()
        val inFlight = cacheFuture

        if (inFlight?.isDone == true) {
            cacheFuture = null
        }

        val result = when {
            inFlight != null && !inFlight.isDone -> inFlight
            isWithinInterval && storedConfig != null -> ValueFuture<Void?>(null)
            else -> {
                val currentFuture = taskExecutor.executeAsFuture(
                    FixedRetryingTask(
                        fetchRemoteConfig(),
                        attributionParams.deviceInfo,
                        logger,
                        TrackingEventErrorClassifier.instance,
                        null,
                        retryConfig,
                    ),
                )
                cacheFuture = currentFuture
                currentFuture
            }
        }

        return result
    }

    private fun fetchRemoteConfig(): Task<Void?> {
        return Task {
            val installInstanceIdValue = attributionParams.installInstanceIdProvider().await()
            val advertiseIdValue = attributionParams.deviceIdProvider().await().advertiserId
            val userIdValue = attributionParams.userIdProvider().await()
            val deviceInfo = attributionParams.deviceInfo
            val sdkVersion = attributionParams.sdkVersion

            val queryParams = RemoteConfigQueryParams(
                installInstanceId = installInstanceIdValue,
                osVersion = deviceInfo.osVersion,
                appVersionCode = deviceInfo.getAppVersion().getVersionCode(),
                appVersionName = deviceInfo.getAppVersion().getVersionName(),
                sdkVersionMajor = sdkVersion.major.toString(),
                sdkVersionMinor = sdkVersion.minor.toString(),
                sdkVersionPatch = sdkVersion.patch.toString(),
                sdkVersionName = sdkVersion.name,
                sdkVersionPlatform = "android",
                sdkVersionWrapper = sdkVersion.platformType.wrapper,
                deviceType = deviceInfo.getDeviceType().toString(),
                deviceModel = deviceInfo.deviceModel,
                countryIso2 = deviceInfo.getCountryIso(),
                deviceTimestamp = remoteConfigTimeStamp.getCurrentTimestamp(),
                attributionTimestamp = remoteConfigTimeStamp.getFirstAttributionTimestamp(),
                firstSdkInitTimestamp = remoteConfigTimeStamp.getFirstInitializedAtTimestamp(),
                installTimestamp = remoteConfigTimeStamp.getInstalledAtTimestamp(),
            )

            val result = httpClient.fetchRemoteConfig(
                logger,
                queryParams,
                advertiseIdValue,
                userIdValue,
                installInstanceIdValue,
            )

            if (result.isSuccess) {
                val response = result.getOrNull() ?: throw NullPointerException("fetchRemoteConfig failed with null response")
                val resultJson = response.body
                val retryAfterSeconds = response.retryAfterSeconds

                if (retryAfterSeconds != null && remoteConfigStore.getRetryAfterSeconds() == null) {
                    setRetryAfterSeconds(retryAfterSeconds)
                }

                setPreviousFetchTimeStamp()
                storeConfig(resultJson)
            } else {
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    throw exception
                }
            }

            null
        }
    }

    internal fun getValue(configKey: String): String? {
        val storedConfig = remoteConfigStore.getStoredAssignments()
        return storedConfig?.get(configKey)?.configValue
    }

    internal fun getAll(): List<Assignment>? {
        val storedConfig = remoteConfigStore.getStoredAssignments()
        return storedConfig?.values?.toList()
    }

    internal fun storeConfig(configJson: JSONObject) {
        val assignments = Assignment.parseAssignments(configJson)
        val storedAssignments = remoteConfigStore.getStoredAssignments().orEmpty()
        val mergedAssignments = storedAssignments.toMutableMap()
        for (assignment in assignments) {
            val existing = mergedAssignments[assignment.configKey]
            val mergedAssignment = if (existing != null) {
                assignment.copy(isPending = existing.isPending)
            } else {
                assignment.copy(isPending = false)
            }
            mergedAssignments[assignment.configKey] = mergedAssignment
        }
        val mergedJson = JSONObject()
        val mergedArray = JSONArray()
        for (assignment in mergedAssignments.values) {
            mergedArray.put(assignment.toJson())
        }
        mergedJson.put("assignments", mergedArray)
        val rawConfig = mergedJson.toString()
        remoteConfigStore.setStoredAssignments(rawConfig)
    }

    internal fun setConfig(settings: JusttrackRemoteConfigSettings) {
        currentFetchInterval = settings.minimumFetchIntervalInSeconds
        remoteConfigStore.setMinimumIntervalInSecond(settings.minimumFetchIntervalInSeconds)
    }

    /**
     * Return whether the current time is within the minimum fetch interval.
     */
    private fun isWithinInterval(previousFetchTimeStamp: Long?, currentFetchInterval: Long): Boolean {
        return if (previousFetchTimeStamp == null) {
            false
        } else {
            val millisecondToSecond = MILLISECOND_TO_SECOND
            System.currentTimeMillis() - previousFetchTimeStamp < currentFetchInterval * millisecondToSecond
        }
    }

    private fun setPreviousFetchTimeStamp() {
        val currentTime = System.currentTimeMillis()
        previousFetchTimeStamp = currentTime
        remoteConfigStore.setPreviousFetchTimeStamp(currentTime)
    }

    private fun setRetryAfterSeconds(value: Int) {
        remoteConfigStore.setRetryAfterSeconds(value)
    }

    private companion object {
        const val MILLISECOND_TO_SECOND = 1000
    }
}
