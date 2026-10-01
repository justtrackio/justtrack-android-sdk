package io.justtrack

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.justtrack.log.Logger
import io.justtrack.providers.AdvertiserIdProvider
import io.justtrack.versions.SdkVersion
import io.justtrack.workManager.CoroutineTimeTicker
import io.justtrack.workManager.DefaultCoroutineTimeTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Create a scheduler that will enqueue a worker to send remaining events, metrics, and messages to server.
 * The ticker will be responsible to reschedule the worker timeslot. When "onStop" is called the ticker
 * will be removed and the worker will not be reschedule and able to execute task.
 *
 * Using delay with WorkManager does not guarantee the correct execute time. Each phone model handles
 * WorkManager differently. Ex : samsung with optimise battery will execute it when user try
 * to delete the application
 */
internal class WorkerScheduler @JvmOverloads constructor(
    private val config: Config,
    tickerFactory: () -> CoroutineTimeTicker = { DefaultCoroutineTimeTicker() },
    private val intentFactory: (Context) -> Intent = { context ->
        Intent(context, BackgroundSenderTaskReceiver::class.java)
    },
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val installInstanceIdFunction: () -> AsyncFuture<String>,
) {
    private val rescheduleTicker = tickerFactory()

    private val alarmManager: AlarmManager =
        config.appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun startScheduling() {
        CoroutineScope(Dispatchers.IO).launch {
            enqueueWorker()
        }
    }

    fun onResume() {
        rescheduleTicker.startTicking(
            REQUEUE_INTERVAL_MILLIS,
            onTick = {
                enqueueWorker()
            },
        )
    }

    fun onPause() {
        rescheduleTicker.stopTicking()
    }

    private suspend fun enqueueWorker() {
        if (config.isTracking.get()) {
            val intent = intentFactory(config.appContext)

            val intentData = createInputData(intent) ?: return

            val pendingIntent = PendingIntent.getBroadcast(
                config.appContext,
                0,
                intentData,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            val delayTime = if (BuildConfig.DEBUG) {
                TEST_WORKER_DELAY_MILLIS
            } else {
                WORKER_DELAY_MILLIS
            }
            val triggerAt = elapsedRealtimeProvider() + delayTime
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private suspend fun createInputData(intent: Intent): Intent? {
        return try {
            val userId = UUID.fromString(config.userIdProvider.provideUserIdFuture().await())
            val advertiserId = config.advertiserIdProvider.provideAdvertiserId().await().advertiserId
            val installInstanceId = installInstanceIdFunction.invoke().await()

            WorkerInputData(
                config.apiToken,
                config.environment,
                advertiserId ?: "",
                config.trackingId ?: "",
                config.trackingProvider,
                userId,
                installInstanceId,
                installInstanceId,
                config.sdkVersion,
                config.isLogEnabled,
                config.applicationInfo.packageName,
                config.applicationInfo.version,
            ).appendDataToIntent(intent)
            intent
        } catch (_: Exception) {
            config.logger.debug("Unable to createInputData due to missing userId")
            null
        }
    }

    internal data class ApplicationInfo(
        internal val packageName: String,
        internal val version: ApplicationVersion,
    )

    internal data class Config(
        internal val appContext: Context,
        internal val apiToken: String,
        internal val sdkVersion: SdkVersion,
        internal val isTracking: AtomicBoolean,
        internal val logger: Logger,
        internal val isLogEnabled: Boolean,
        internal val applicationInfo: ApplicationInfo,
        internal val environment: Environment,
        internal val advertiserIdProvider: AdvertiserIdProvider,
        internal val trackingId: String?,
        internal val trackingProvider: String,
        internal val userIdProvider: UserIdProvider,
    )

    internal companion object {
        private const val REQUEUE_INTERVAL_MILLIS = 30_000L

        // we unmark stuff after 10 minutes normally - so schedule the work manager to run at that time, too.
        // (if the OS accepts that time and doesn't put us on a different time slot anyway)
        // for sandbox, we just change it to 1 minute in code anyway for faster testing
        internal const val WORKER_DELAY_MILLIS = 10 * 60 * 1000L
        internal const val TEST_WORKER_DELAY_MILLIS = 40 * 1000L
    }
}
