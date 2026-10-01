package io.justtrack

import androidx.annotation.VisibleForTesting
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import io.justtrack.JustTrack.Companion.notifyAppStart
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
import io.justtrack.executor.SerializeHandlerThread
import io.justtrack.executor.TaskExecutor
import io.justtrack.executor.TaskExecutorImpl
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Creates a [TaskExecutor] that runs all tasks synchronously on the calling thread,
 * suitable for use in androidTests where real threading is not needed.
 */
internal fun immediateTaskExecutor(): TaskExecutor = TaskExecutorImpl(
    object : ExecutorService by java.util.concurrent.Executors.newSingleThreadExecutor() {
        override fun execute(command: Runnable) = command.run()
        override fun <T> submit(task: java.util.concurrent.Callable<T>): Future<T> {
            val result = task.call()
            return object : Future<T> {
                override fun cancel(mayInterruptIfRunning: Boolean) = false
                override fun isCancelled() = false
                override fun isDone() = true
                override fun get() = result
                override fun get(timeout: Long, unit: TimeUnit) = result
            }
        }
    },
    false,
    object : SerializeHandlerThread {
        override fun run(runnable: Runnable) = runnable.run()
    },
)

fun getBadResponseBody(throwable: Throwable?): String? {
    if (throwable is BadResponseException) {
        return throwable.body
    }

    val cause = throwable?.cause

    return if (cause != null) getBadResponseBody(cause) else null
}

@VisibleForTesting
internal fun createForTesting(
    builder: JustTrackSdkBuilder,
    retryConfig: RetryConfig,
    claimProvider: ClaimProvider?,
    standardIntegrityTokenProvider: StandardIntegrityTokenProvider?,
    attributionApi: AttributionApi = DefaultAttributionApi(),
    configApi: ConfigApi = DefaultConfigApi(),
    eventApi: EventApi = DefaultEventApi(),
    experimentApi: ExperimentApi = DefaultExperimentApi(),
    integrityApi: IntegrityApi = DefaultIntegrityApi(),
    logApi: LogApi = DefaultLogApi(),
    privacyApi: PrivacyApi = DefaultPrivacyApi(),
): JustTrackSdkImpl {
    val sdk = JustTrackSdkImpl(
        builder,
        attributionApi,
        configApi,
        eventApi,
        experimentApi,
        integrityApi,
        logApi,
        privacyApi,
        retryConfig,
        claimProvider,
        builder.startConfigBuilder.build(),
        standardIntegrityTokenProvider,
    )
    // need to call init because the instanceManager normally does this
    sdk.init(builder)
    // set the instance on the instance manager so other services can find the correct SDK
    InstanceManager.setInstance(sdk)
    // notify about the app start because the init provider no longer does this for a test
    notifyAppStart()

    sdk.start()

    return sdk
}
