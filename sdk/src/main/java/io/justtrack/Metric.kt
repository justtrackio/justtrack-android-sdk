package io.justtrack

import io.justtrack.events.MetricUnit
import io.justtrack.log.LoggerFields

/**
 * Describes a named metric that can be published through the SDK's logging system.
 *
 * @property metric The metric identifier string sent to the backend.
 * @property unit   The unit of measurement for this metric.
 */
class Metric internal constructor(
    val metric: String,
    private val defaultDimensions: Map<String, String> = emptyMap(),
    val unit: MetricUnit = MetricUnit.COUNT,
) : LoggerFields {
    override val fields: Map<String, String>
        get() = defaultDimensions
}
