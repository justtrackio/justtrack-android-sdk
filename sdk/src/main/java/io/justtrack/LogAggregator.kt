package io.justtrack

import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.DTOLogMetric

internal interface LogAggregator : AutoCloseable {
    fun addLogMessage(message: DTOLogMessage)

    fun addLogMetric(metric: DTOLogMetric)

    fun sendLogsAndMetrics(sender: LogSender)

    interface LogSender {
        suspend fun sendLogsAndMetrics(messages: Collection<DTOLogMessage>, metrics: Collection<DTOLogMetric>): Result<Unit>
    }
}
