package io.justtrack

import android.content.Context
import io.justtrack.events.JtAppInstallEvent
import io.justtrack.events.JtAppOpenEvent
import io.justtrack.events.TimeUnitGroup
import io.justtrack.log.Logger
import io.justtrack.log.LoggerFieldsBuilder
import java.util.Date

internal class NotifyAppStartHandler internal constructor(
    val sdk: BaseJustTrackSdk,
    val taskExecutor: TaskExecutor,
    val context: Context,
    private val sessionManager: SessionManager,
    private val startEvent: AppStartDuration,
    private val applicationVersionUpdateInfoFuture: AsyncFuture<AppVersionUpdateInfo?>,
    val logger: Logger,
) {

    @JvmName("notifyAppStart")
    internal fun notifyAppStart() {
        val sessionId: String = sessionManager.getLatestSessionId()
        val duration = startEvent.getCompletionDuration()
        val happenedAt = startEvent.getStartedAt()
        sdk.publishEvent(JtAppOpenEvent(sessionId, duration, TimeUnitGroup.MILLISECONDS, happenedAt))

        taskExecutor.executeAsFuture(
            NotifyAppStartTask(
                sdk,
                applicationVersionUpdateInfoFuture,
                sessionId,
                duration,
                happenedAt,
                logger,
            ),
        )
    }

    internal class NotifyAppStartTask(
        val sdk: BaseJustTrackSdk,
        val applicationVersionUpdateInfoFuture: AsyncFuture<AppVersionUpdateInfo?>,
        val sessionId: String,
        val duration: Double,
        val happenedAt: Date,
        val logger: Logger,
    ) : Task<Boolean> {
        override suspend fun execute(): Boolean {
            val applicationVersionUpdateInfo = applicationVersionUpdateInfoFuture.await() ?: return false

            when (applicationVersionUpdateInfo.kind) {
                AppVersionUpdateKind.INSTALLED_APP -> sdk.publishEvent(JtAppInstallEvent(sessionId, duration, TimeUnitGroup.MILLISECONDS, happenedAt))
                AppVersionUpdateKind.UPDATED_APP -> {
                    val previousVersion: String = applicationVersionUpdateInfo.appLastVersion.getVersionName()
                    logger.info("App was updated", LoggerFieldsBuilder().with("previous_app_version_code", previousVersion))
                }

                AppVersionUpdateKind.NO_CHANGE -> {}
                else -> {}
            }
            return true
        }
    }
}
