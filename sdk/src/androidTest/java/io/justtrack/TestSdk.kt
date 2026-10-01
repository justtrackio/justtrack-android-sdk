package io.justtrack

import android.content.Context
import android.content.Intent
import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.api.AttributionApi
import io.justtrack.api.ConfigApi
import io.justtrack.api.DefaultAttributionApi
import io.justtrack.api.DefaultConfigApi
import io.justtrack.api.DefaultEventApi
import io.justtrack.api.DefaultExperimentApi
import io.justtrack.api.DefaultIntegrityApi
import io.justtrack.api.DefaultLogApi
import io.justtrack.api.DefaultPrivacyApi
import io.justtrack.api.EventApi
import io.justtrack.api.ExperimentApi
import io.justtrack.api.IntegrityApi
import io.justtrack.api.LogApi
import io.justtrack.api.PrivacyApi
import io.justtrack.attribution.Attribution
import io.justtrack.retargeting.PreliminaryRetargetingParameters
import io.justtrack.retargeting.RetargetingParameters
import io.justtrack.util.ExecutorServiceFactory
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersionImpl
import java.util.Date
import java.util.UUID

internal open class TestSdk(
    context: Context,
    executorBuilder: ExecutorServiceFactory,
    runCallbacksSerially: Boolean,
    val databaseInterface: DatabaseInterface? = null,
    attributionApi: AttributionApi = DefaultAttributionApi(),
    configApi: ConfigApi = DefaultConfigApi(),
    eventApi: EventApi = DefaultEventApi(),
    experimentApi: ExperimentApi = DefaultExperimentApi(),
    integrityApi: IntegrityApi = DefaultIntegrityApi(),
    logApi: LogApi = DefaultLogApi(),
    privacyApi: PrivacyApi = DefaultPrivacyApi(),
    retryConfig: RetryConfig = RetryConfig.DEFAULT_CONFIG,
) :
    JustTrackSdkImpl(
        context,
        "io.justtrack.test",
        "token",
        null,
        executorBuilder,
        databaseInterface ?: DatabaseInterface(context, LoggerImpl()),
        ApplicationVersionImpl("1.0.0", "1"),
        SdkVersionImpl(7, 0, 0, "7.0.0", PlatformType.ANDROID),
        LoggerImpl(),
        DeviceInfoImpl(context),
        Environment(),
        true,
        attributionApi,
        configApi,
        eventApi,
        experimentApi,
        integrityApi,
        logApi,
        privacyApi,
        retryConfig,
        null,
        JustTrackSdkConfig.Builder().build(),
        null,
        runCallbacksSerially,
        listOf(),
        null,
        ReAttributionConfig(),
        1000,
        false,
    ),
    ConnectivityProvider {
    private val reconnectSubscriptions: SubscriptionManager<ConnectivityProvider.ConnectivityCallback> =
        SubscriptionManager()

    override val connectionType: ConnectionType = ConnectionType.UNKNOWN

    init {
        publishEventsQueue.maxBatchSize = 1
        publishEventsQueue.start(this)
    }

    override val attribution: AsyncFuture<Attribution>
        get() = run {
            val userIdString = userIdProvider.provideUserIdFuture().get()
            val userId = UUID.fromString(userIdString)
            val installId = attributionIdManager.getOrCreateInstallId().get()
            return ValueFuture(
                AttributionImpl(
                    AttributionResponseImpl(
                        userId,
                        installId,
                        "acquisition",
                        CampaignImpl("1", "Test campaign", "acquisition", false),
                        ChannelImpl(1, "Test channel", false),
                        PartnerImpl(1, "Test network"),
                        null,
                        null,
                        null,
                        null,
                        Date(),
                        false,
                    ),
                ),
            )
        }

    override val retargetingParameters: AsyncFuture<RetargetingParameters>
        get() = ErrorFuture(Exception("not implemented"))

    override val preliminaryRetargetingParameters: PreliminaryRetargetingParameters?
        get() = null

    override fun installUncaughtExceptionHandler() {
        crashHandler.installUncaughtExceptionHandler()
    }

    override fun handleNewIntent(newIntent: Intent?, isAutomatic: Boolean) {
        // ignore, we don't care
    }

    override val installInstanceId: AsyncFuture<String>
        get() = installInstanceIdInternal

    override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
        return reconnectSubscriptions.subscribe(callback)
    }

    fun callReconnectSubscriptions() {
        reconnectSubscriptions.call { listener -> listener.onConnectivityChange(true) }
    }

    fun <T> runTask(task: AsyncFuture<T>, delayMS: Long, callback: Callback<T>) {
        task.registerCallback(object : Callback<T> {
            override fun resolve(response: T) {
                Thread.sleep(delayMS)
                callback.resolve(response)
            }

            override fun reject(exception: Throwable) {
                callback.reject(exception)
            }
        })
    }
}
