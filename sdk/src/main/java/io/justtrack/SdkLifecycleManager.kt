package io.justtrack

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.justtrack.crashes.ANRDetector
import io.justtrack.executor.TaskExecutor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

internal class SdkLifecycleManager(
    private val deps: Dependencies,
    private val callbacks: Callbacks,
) {

    private var reconnectSubscription: Subscription? = null

    internal data class Dependencies(
        val context: Context,
        val logger: HttpLogger,
        val taskExecutor: TaskExecutor,
        val executor: ExecutorService,
        val sessionManager: SessionManager,
        val connectivityProvider: ConnectivityProvider,
        val publishEventsQueue: PublishEventsQueue,
        val periodicLogsPublisher: PeriodicLogsPublisher,
        val anrDetector: ANRDetector,
        val workerScheduler: WorkerScheduler,
        val networkErrorLogger: NetworkErrorLogger,
        val isTracking: AtomicBoolean,
        val appVersionProvider: AppVersionProvider,
    )

    internal data class Callbacks(
        val onReconnect: Runnable,
        val retrySendPersistId: (String) -> Unit,
        val handleNewIntent: (Intent?, Boolean) -> Unit,
        val clearInstance: Runnable,
    )

    fun init(application: Context, sdk: JustTrackSdkImpl) {
        Handler(deps.context.mainLooper).post {
            @Suppress("TooGenericExceptionCaught")
            try {
                val lc = ProcessLifecycleOwner.get().lifecycle
                if (lc.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    deps.sessionManager.onResume()
                } else {
                    val observer = object : LifecycleEventObserver {
                        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                            if (event == Lifecycle.Event.ON_RESUME) {
                                lc.removeObserver(this)
                                deps.sessionManager.onResume()
                            }
                        }
                    }
                    lc.addObserver(observer)
                }
            } catch (exception: Throwable) {
                deps.logger.warn("Failed to check lifecycle of process", exception)
            }
        }

        JustTrack.initWithSdk(application, sdk)
        reconnectSubscription = deps.connectivityProvider.registerOnReconnected(
            object : ConnectivityProvider.ConnectivityCallback {
                override fun onConnectivityChange(connected: Boolean) {
                    if (connected) {
                        callbacks.onReconnect.run()
                    }
                }
            },
        )
        deps.publishEventsQueue.start(deps.connectivityProvider)
        callbacks.retrySendPersistId(PersistentIdStore.REASON_APP_START)
    }

    fun onResume(activity: Activity) {
        val intent = activity.intent
        deps.taskExecutor.execute(
            {
                callbacks.handleNewIntent(intent, true)
                deps.sessionManager.onResume()
                if (deps.isTracking.get()) {
                    deps.workerScheduler.onResume()
                }
                deps.anrDetector.start()
            },
            { exception ->
                deps.networkErrorLogger.logException(
                    deps.logger,
                    exception,
                    "Could not handle app resume, SDK is shutting down",
                )
            },
        )
        deps.periodicLogsPublisher.start()
    }

    fun onPause() {
        deps.sessionManager.onPause()
        deps.periodicLogsPublisher.pause()
        deps.workerScheduler.onPause()
        deps.anrDetector.stop()
    }

    fun shutdown() {
        callbacks.clearInstance.run()
        reconnectSubscription?.unsubscribe()
        reconnectSubscription = null
        deps.connectivityProvider.shutdown()

        @Suppress("TooGenericExceptionCaught")
        try {
            deps.sessionManager.shutdown()
            deps.publishEventsQueue.close()
            deps.logger.close()
        } catch (exception: Exception) {
            deps.networkErrorLogger.logException(
                deps.logger,
                exception,
                "Failed to close resources",
            )
        }
        deps.periodicLogsPublisher.stop()
        deps.taskExecutor.execute(
            { deps.executor.shutdown() },
            { exception ->
                deps.networkErrorLogger.logException(
                    deps.logger,
                    exception,
                    "Could not shut down SDK, a shutdown is already in progress",
                )
            },
        )
    }

    fun notifyAppStart(sdk: JustTrackSdkImpl, startEvent: AppStartDuration) {
        NotifyAppStartHandler(
            sdk,
            deps.taskExecutor,
            deps.context,
            deps.sessionManager,
            startEvent,
            deps.appVersionProvider.provideAppVersionUpdateInfo(),
            deps.logger,
        ).notifyAppStart()
    }
}
