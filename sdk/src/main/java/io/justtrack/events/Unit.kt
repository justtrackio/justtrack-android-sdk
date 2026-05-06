package io.justtrack.events

import io.justtrack.AppEvent

/**
 * An enum describing how to interpret the value of an [AppEvent].
 */
enum class Unit(private val unit: String) {
    /**
     * We want to count how many times something happened in total.
     */
    COUNT("count"),

    /**
     * We want to measure how long something takes with millisecond precision.
     */
    MILLISECONDS("milliseconds"),

    /**
     * We want to measure how long something takes with second precision.
     */
    SECONDS("seconds"),
    ;

    override fun toString(): String {
        return unit
    }

    internal companion object {
        internal fun fromString(string: String): Unit? {
            for (unit in entries) {
                if (unit.toString() == string) {
                    return unit
                }
            }
            return null
        }
    }
}
