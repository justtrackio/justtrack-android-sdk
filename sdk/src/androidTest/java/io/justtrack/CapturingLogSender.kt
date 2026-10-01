package io.justtrack

import io.justtrack.dtos.DTOLogMessage
import io.justtrack.dtos.DTOLogMetric
import java.util.concurrent.atomic.AtomicInteger

internal class CapturingLogSender : LogAggregator.LogSender {
    val sendCount = AtomicInteger(0)
    val sentMessages = mutableListOf<List<DTOLogMessage>>()
    val sentMetrics = mutableListOf<List<DTOLogMetric>>()

    override suspend fun sendLogsAndMetrics(messages: Collection<DTOLogMessage>, metrics: Collection<DTOLogMetric>): Result<Unit> {
        sendCount.incrementAndGet()
        synchronized(this) {
            sentMessages.add(messages.toList())
            sentMetrics.add(metrics.toList())
        }
        return Result.success(Unit)
    }
}
