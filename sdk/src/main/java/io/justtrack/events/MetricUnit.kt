package io.justtrack.events

/**
 * Defines the unit of measurement for a [io.justtrack.Metric].
 *
 * @property unit The backend-encoded name of this unit.
 */
enum class MetricUnit(val unit: String) {
    /** Plain count of occurrences. */
    COUNT("Count"),

    /** Duration measured in seconds. */
    SECONDS("Seconds"),

    /** Duration measured in milliseconds. */
    MILLISECONDS("Milliseconds"),
}
