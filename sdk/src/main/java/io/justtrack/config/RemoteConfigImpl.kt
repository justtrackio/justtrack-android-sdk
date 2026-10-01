package io.justtrack.config

import android.content.Context
import io.justtrack.AsyncFuture
import io.justtrack.DatabaseInterface
import io.justtrack.DeviceInfo
import io.justtrack.ErrorFuture
import io.justtrack.SdkFirstInitializationTimestampRepo
import io.justtrack.UserIdProvider
import io.justtrack.executor.TaskExecutor
import io.justtrack.api.ConfigApi
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.log.Logger
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.versions.SdkVersion
import java.util.concurrent.atomic.AtomicBoolean

internal class RemoteConfigImpl internal constructor(
    context: Context,
    private val isTracking: AtomicBoolean,
    attributionParams: AttributionParams,
    sdkFirstInitializationTimestampRepo: SdkFirstInitializationTimestampRepo,
    private val taskExecutor: TaskExecutor,
    configApi: ConfigApi,
    logger: Logger,
) : RemoteConfig {
    private val retryTimeouts: List<Int> = listOf(FIRST_RETRY_ATTEMPT, SECOND_RETRY_ATTEMPT, THIRD_RETRY_ATTEMPT)
    private val remoteConfigTimeStamp = RemoteConfigTimestampImpl(
        context,
        attributionParams.attributionDatabase,
        sdkFirstInitializationTimestampRepo,
        logger,
    )
    private val remoteConfigStore = RemoteConfigStoreImpl(context)
    private val remoteConfigProvider = RemoteConfigProvider(
        remoteConfigStore,
        taskExecutor,
        configApi,
        attributionParams,
        remoteConfigTimeStamp,
        logger,
        retryTimeouts,
    )

    private val remoteConfigActivator = RemoteConfigActivator(
        remoteConfigStore,
        taskExecutor,
        configApi,
        attributionParams,
        logger,
        retryTimeouts,
    )

    override fun fetch(): AsyncFuture<Void?> {
        if (!isTracking.get()) {
            return ErrorFuture(SdkNotTrackingException())
        }
        return remoteConfigProvider.fetch()
    }

    override fun activate(experiments: List<String>): AsyncFuture<Void?> {
        if (!isTracking.get()) {
            return ErrorFuture(SdkNotTrackingException())
        }

        return remoteConfigActivator.activate(experiments)
    }

    override fun fetchAndActivate(): AsyncFuture<Void?> {
        if (!isTracking.get()) {
            return ErrorFuture(SdkNotTrackingException())
        }

        return taskExecutor.executeFuture {
            fetch().await()
            val experiments = getAll()?.map { it.experimentId }
            if (experiments != null) {
                activate(experiments).await()
            }

            null
        }
    }

    override fun setConfig(settings: JusttrackRemoteConfigSettings) {
        remoteConfigProvider.setConfig(settings)
    }

    override fun getAll(): List<Assignment>? {
        return remoteConfigProvider.getAll()
    }

    override fun getBoolean(configKey: String): Boolean? {
        val value = remoteConfigProvider.getValue(configKey)

        if (value == null) {
            return null
        } else {
            val normalized = value.trim().lowercase()
            return when (normalized) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
    }

    override fun getDouble(configKey: String): Double? {
        val value = remoteConfigProvider.getValue(configKey)

        return value?.toDoubleOrNull()
    }

    override fun getInt(configKey: String): Int? {
        val value = remoteConfigProvider.getValue(configKey)

        return value?.toIntOrNull()
    }

    override fun getLong(configKey: String): Long? {
        val value = remoteConfigProvider.getValue(configKey)

        return value?.toLongOrNull()
    }

    override fun getString(configKey: String): String? {
        return remoteConfigProvider.getValue(configKey)
    }

    internal data class AttributionParams(
        internal val installInstanceIdProvider: () -> AsyncFuture<String>,
        internal val userIdProvider: UserIdProvider,
        internal val advertiserIdProvider: AdvertiserIdProvider,
        internal val attributionDatabase: DatabaseInterface,
        internal val deviceInfo: DeviceInfo,
        internal val sdkVersion: SdkVersion,
    )

    private companion object {
        const val FIRST_RETRY_ATTEMPT = 10
        const val SECOND_RETRY_ATTEMPT = 20
        const val THIRD_RETRY_ATTEMPT = 30
    }
}
