package io.justtrack.events

/**
 * A time-based unit grouping the [Unit.MILLISECONDS] and [Unit.SECONDS] [Unit]s.
 */
enum class TimeUnitGroup(
    /** The underlying [Unit] this group maps to. */
    val base: Unit,
) {
    /**
     * @see Unit.MILLISECONDS
     */
    MILLISECONDS(Unit.MILLISECONDS),

    /**
     * @see Unit.SECONDS
     */
    SECONDS(Unit.SECONDS),
    ;

    override fun toString(): String {
        return base.toString()
    }
}
