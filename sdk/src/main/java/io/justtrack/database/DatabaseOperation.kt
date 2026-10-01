package io.justtrack.database

import io.justtrack.AppVersionUpdateInfo
import io.justtrack.ApplicationVersion
import io.justtrack.AttributionOutput
import io.justtrack.AttributionResponse
import io.justtrack.AttributionTimestamps
import io.justtrack.LogMessageEntity
import io.justtrack.LogMetricEntity
import io.justtrack.UserEventEntity
import kotlinx.coroutines.channels.SendChannel

internal sealed class DatabaseOperation {
    data class InsertMessage(
        val message: LogMessageEntity,
        val resultChannel: SendChannel<Long?>,
    ) : DatabaseOperation()

    data class InsertMetric(
        val metric: LogMetricEntity,
        val resultChannel: SendChannel<Long?>,
    ) : DatabaseOperation()

    data class InsertEvent(
        val event: UserEventEntity,
        val resultChannel: SendChannel<Pair<Long, Long>?>,
    ) : DatabaseOperation()

    data class DeleteMessage(
        val idList: List<Long>? = null,
        val cutOffMS: Long? = null,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class DeleteMetric(
        val idList: List<Long>? = null,
        val cutOffMS: Long? = null,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class DeleteEvent(
        val idList: List<Long>? = null,
        val cutOffMS: Long? = null,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class MarkMessage(
        val idList: List<Long>,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class MarkMetric(
        val idList: List<Long>,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class MarkEvent(
        val idList: List<Long>,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class UnMarkMessage(
        val idList: List<Long>,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class UnMarkMetric(
        val idList: List<Long>,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class UnMarkEvent(
        val idList: List<Long>,
        val resultChannel: SendChannel<Boolean>,
    ) :
        DatabaseOperation()

    data class GetNextBatchAndMarkMessage(
        val batchSize: Int = 100,
        val resultChannel: SendChannel<List<LogMessageEntity>>,
    ) : DatabaseOperation()

    data class GetNextBatchAndMarkMetric(
        val batchSize: Int = 100,
        val resultChannel: SendChannel<List<LogMetricEntity>>,
    ) : DatabaseOperation()

    data class GetNextBatchAndMarkEvent(
        val batchSize: Int = 100,
        val resultChannel: SendChannel<List<UserEventEntity>>,
    ) : DatabaseOperation()

    data class GetAllMessage(
        val unMarkOnly: Boolean = false,
        val resultChannel: SendChannel<List<LogMessageEntity>>,
    ) : DatabaseOperation()

    data class GetAllMetric(
        val unMarkOnly: Boolean = false,
        val resultChannel: SendChannel<List<LogMetricEntity>>,
    ) : DatabaseOperation()

    data class GetAllEvent(
        val unMarkOnly: Boolean = false,
        val resultChannel: SendChannel<List<UserEventEntity>>,
    ) : DatabaseOperation()

    data class SetIntegrityTokenSent(
        val isSent: Boolean,
        val resultChannel: SendChannel<Boolean>,
    ) : DatabaseOperation()

    data class IsIntegrityTokenSent(
        val resultChannel: SendChannel<Boolean>,
    ) : DatabaseOperation()

    data class GetIntegritySecret(
        val resultChannel: SendChannel<String?>,
    ) : DatabaseOperation()

    data class SetIntegritySecret(
        val secret: String,
        val resultChannel: SendChannel<Boolean>,
    ) : DatabaseOperation()

    data class SetAttributionFinished(
        val response: AttributionResponse,
        val resultChannel: SendChannel<Boolean>,
    ) : DatabaseOperation()

    data class GetStoredOutput(
        val resultChannel: SendChannel<AttributionOutput?>,
    ) : DatabaseOperation()

    data class GetAttributionTimestamps(
        val resultChannel: SendChannel<AttributionTimestamps?>,
    ) : DatabaseOperation()

    data class SetLastOpen(
        val resultChannel: SendChannel<Boolean>,
        val currentMs: Long,
    ) : DatabaseOperation()

    data class GetAppVersionUpdateInfo(
        val currentApplicationVersion: ApplicationVersion,
        val resultChannel: SendChannel<AppVersionUpdateInfo>,
    ) : DatabaseOperation()

    data class GetInstallId(
        val resultChannel: SendChannel<String?>,
    ) : DatabaseOperation()

    data class GetUserId(
        val resultChannel: SendChannel<String?>,
    ) : DatabaseOperation()

    data class SetInstallId(
        val installId: String,
        val resultChannel: SendChannel<Boolean>,
    ) : DatabaseOperation()

    data class SetUserId(
        val userId: String,
        val resultChannel: SendChannel<Boolean>,
    ) : DatabaseOperation()

    object NukeMessage : DatabaseOperation()

    object NukeMetric : DatabaseOperation()

    object NukeEvent : DatabaseOperation()
}
