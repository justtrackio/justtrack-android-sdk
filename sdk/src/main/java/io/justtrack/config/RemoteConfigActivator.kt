package io.justtrack.config

import io.justtrack.AsyncFuture
import io.justtrack.FixedRetryingTask
import io.justtrack.HttpClient
import io.justtrack.Task
import io.justtrack.TaskExecutor
import io.justtrack.TrackingEventErrorClassifier
import io.justtrack.dtos.DTOActivateExperiments
import io.justtrack.log.Logger
import org.json.JSONArray
import org.json.JSONObject

internal class RemoteConfigActivator internal constructor(
    private val remoteConfigStore: RemoteConfigStore,
    private val taskExecutor: TaskExecutor,
    private val httpClient: HttpClient,
    private val attributionParams: RemoteConfigImpl.AttributionParams,
    private val logger: Logger,
    private val retryTimeouts: List<Int>,
) {

    internal fun activate(experiments: List<String>): AsyncFuture<Void?> {
        return taskExecutor.executeAsFuture(
            FixedRetryingTask(
                activateRemoteConfig(experiments),
                attributionParams.deviceInfo,
                logger,
                TrackingEventErrorClassifier.instance,
                null,
                retryTimeouts,
            ),
        )
    }

    private fun activateRemoteConfig(experiments: List<String>): Task<Void?> {
        return Task {
            if (experiments.isEmpty()) return@Task null

            val installInstanceId = attributionParams.installInstanceIdProvider().await()
            val body = DTOActivateExperiments(installInstanceId, experiments)
            val result = httpClient.activateExperiments(
                logger,
                body,
                attributionParams.deviceIdProvider().await().advertiserId,
                attributionParams.userIdProvider().await(),
                installInstanceId,
            )

            if (result.isSuccess) {
                updateStoredPendingStatus(experiments)
            } else {
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    throw exception
                }
            }

            null
        }
    }

    private fun updateStoredPendingStatus(experiments: List<String>) {
        if (experiments.isEmpty()) return

        val storedAssignments = remoteConfigStore.getStoredAssignments() ?: return
        val experimentIds = experiments.toHashSet()
        val updatedAssignments = ArrayList<Assignment>(storedAssignments.size)
        var updated = false

        for (assignment in storedAssignments.values) {
            if (!assignment.isPending && assignment.experimentId in experimentIds) {
                updatedAssignments.add(assignment.copy(isPending = true))
                updated = true
            } else {
                updatedAssignments.add(assignment)
            }
        }

        if (!updated) return

        val assignmentsJson = JSONArray()
        for (assignment in updatedAssignments) {
            assignmentsJson.put(assignment.toJson())
        }

        val storedJson = JSONObject()
        storedJson.put("assignments", assignmentsJson)
        remoteConfigStore.setStoredAssignments(storedJson.toString())
    }
}
