package io.justtrack

import android.content.Context
import android.content.Intent
import io.justtrack.AttributionImpl.CampaignImpl
import io.justtrack.AttributionImpl.ChannelImpl
import io.justtrack.AttributionImpl.PartnerImpl
import io.justtrack.attribution.Attribution
import io.justtrack.retargeting.PreliminaryRetargetingParameters
import io.justtrack.retargeting.RetargetingParameters
import io.justtrack.util.ExecutorServiceFactory
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersionImpl
import org.junit.Assert
import java.util.Date

internal open class TestSdk(
    context: Context,
    executorBuilder: ExecutorServiceFactory,
    httpClient: HttpClient,
    runCallbacksSerially: Boolean,
    val databaseInterface: DatabaseInterface? = null,
) :
    BaseJustTrackSdk(
        context,
        "",
        "io.justtrack.test",
        ApplicationVersionImpl("1.0.0", "1"),
        executorBuilder,
        JustTrackSdkConfig.Builder().build(),
        LoggerImpl(),
        true,
        httpClient,
        RetryConfig.DEFAULT_CONFIG,
        Environment(),
        1000,
        SessionManagerBuilder { _, _, _ -> SessionManager { "sessionId" } },
        runCallbacksSerially,
        databaseInterface ?: DatabaseInterface(context, LoggerImpl()),
        null,
        SdkVersionImpl(7, 0, 0, "7.0.0", PlatformType.ANDROID),
        null,
        listOf(),
        DeviceInfoImpl(context),
    ),
    ConnectivityProvider {
    private val reconnectSubscriptions: SubscriptionManager<ConnectivityProvider.ConnectivityCallback> =
        SubscriptionManager()

    init {
        publishEventsQueue.maxBatchSize = 1
        publishEventsQueue.start(this)
    }

    override val attribution: AsyncFuture<Attribution>
        get() = run {
            val userId = userUUID.get()
            val installId = attributionIdManager.getOrCreateInstallId().get()
            return ValueFuture(
                AttributionImpl(
                    AttributionResponseImpl(
                        userId,
                        installId,
                        "acquisition",
                        CampaignImpl(1, "Test campaign", "acquisition", false),
                        "organic",
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

    override fun getTestGroupId(): AsyncFuture<Int> {
        return ValueFuture(2)
    }

    override val installInstanceId: AsyncFuture<String>
        get() = installInstanceIdInternal

    override fun getAttributionResponse(): AsyncFuture<AttributionResponse> {
        val userId = userUUID.get()
        val installId = attributionIdManager.getOrCreateInstallId().get()
        return ValueFuture(
            AttributionResponseImpl(
                userId,
                installId,
                "acquisition",
                CampaignImpl(1, "Test campaign", "acquisition", false),
                "organic",
                ChannelImpl(1, "Test channel", false),
                PartnerImpl(1, "Test network"),
                null,
                null,
                null,
                null,
                Date(),
                false,
            ),
        )
    }

    override fun registerOnReconnected(callback: ConnectivityProvider.ConnectivityCallback): Subscription {
        return reconnectSubscriptions.subscribe(callback)
    }

    fun callReconnectSubscriptions() {
        reconnectSubscriptions.call { handler ->
            handler.onConnectivityChange(true)
        }
    }

    fun <T> runTask(task: AsyncFuture<T>, delayMS: Long, callback: Callback<T>) {
        callbackInvoker.execute(
            {
                Thread.sleep(delayMS)
                val result = task.get()
                callback.resolve(result)
            },
        ) {
            Assert.fail()
        }
    }
}
