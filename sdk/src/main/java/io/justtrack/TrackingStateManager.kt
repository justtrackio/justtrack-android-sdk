package io.justtrack

import android.content.Context
import io.justtrack.api.PrivacyApi
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.executor.TaskExecutor
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.providers.AdvertiserIdProvider
import java.util.concurrent.atomic.AtomicBoolean

internal class TrackingStateManager(
    private val isTracking: AtomicBoolean,
    private val deps: Dependencies,
    private val components: Components,
    private val integrations: Integrations,
    private val suppliers: Suppliers,
) {

    private var started = false

    internal data class Dependencies(
        val context: Context,
        val logger: HttpLogger,
        val taskExecutor: TaskExecutor,
        val deviceInfo: DeviceInfo,
        val privacyApi: PrivacyApi,
        val userIdProvider: UserIdProvider,
        val advertiserIdProvider: AdvertiserIdProvider,
        val attributionIdManager: AttributionIdManager,
    )

    internal data class Components(
        val sdkFirstInitializationTimestampRepo: SdkFirstInitializationTimestampRepo,
        val sdkConfigDelegate: SdkConfigDelegate,
        val sessionManager: SessionManager,
        val attributionOutputProvider: AttributionOutputProvider,
        val crashReporter: JtCrashReporter,
        val workerScheduler: WorkerScheduler,
        val billingTracker: BillingTracker?,
    )

    internal data class Integrations(
        val pendingIntegrationAdapters: MutableList<IntegrationAdapter>,
        val integratedAdapters: MutableList<IntegrationAdapter>,
    )

    internal data class Suppliers(
        val integrityTokenSupplier: IntegrityTokenSupplier,
        val installInstanceIdSupplier: InstallInstanceIdSupplier,
        val userIdSetter: UserIdSetter,
    )

    fun interface IntegrityTokenSupplier {
        fun publishIntegrityToken(): AsyncFuture<Boolean>
    }

    fun interface InstallInstanceIdSupplier {
        fun getInstallInstanceId(): AsyncFuture<String>
    }

    fun interface UserIdSetter {
        fun setUserId(userId: String): AsyncFuture<Boolean>
    }

    fun isRunning(): Boolean = isTracking.get()

    fun stop() {
        isTracking.set(false)
    }

    fun startWithConfig(config: JustTrackSdkConfig, sdk: JustTrackSdk) {
        isTracking.set(true)

        components.billingTracker?.setEnable(config.automaticIAPTracking)
        components.sdkFirstInitializationTimestampRepo.getOrCreate()

        @Suppress("UNCHECKED_CAST")
        deps.logger.setUser(
            deps.attributionIdManager.getStoredUserId(),
            suppliers.installInstanceIdSupplier.getInstallInstanceId() as AsyncFuture<String?>,
        )

        if (config.userId != null) {
            suppliers.userIdSetter.setUserId(config.userId)
        }

        components.sdkConfigDelegate.applyingConfig(
            config,
            deps.userIdProvider.provideUserIdFuture(),
            deps.advertiserIdProvider,
        )

        if (!started) {
            started = true
            components.sessionManager.start()
            JustTrack.notifyQueuedEvents()
            suppliers.integrityTokenSupplier.publishIntegrityToken()
        }

        synchronized(integrations.pendingIntegrationAdapters) {
            for (pendingAdapter in integrations.pendingIntegrationAdapters) {
                pendingAdapter.integrate(deps.context, sdk, deps.logger)
                integrations.integratedAdapters.add(pendingAdapter)
            }
            integrations.pendingIntegrationAdapters.clear()
        }

        components.attributionOutputProvider.provideAttributionOutput(null)

        components.crashReporter.report()
        components.workerScheduler.startScheduling()
    }

    fun anonymize(): AsyncFuture<Boolean> {
        if (!isTracking.get()) {
            return ErrorFuture(SdkNotTrackingException())
        }

        return AnonymizeUserHandler(
            deps.taskExecutor,
            deps.deviceInfo,
            deps.privacyApi,
            deps.logger,
            AnonymizeUserHandler.AttributionParams(
                deps.userIdProvider.provideUserIdFuture(),
                suppliers.installInstanceIdSupplier.getInstallInstanceId(),
                deps.advertiserIdProvider,
            ),
        ).anonymizeUser()
    }

    fun setAutomaticInAppPurchaseTracking(enabled: Boolean) {
        components.billingTracker?.setEnable(enabled)
    }
}
