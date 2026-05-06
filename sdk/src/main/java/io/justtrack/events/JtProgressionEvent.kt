package io.justtrack.events

import io.justtrack.AppEvent

/**
 * You can use this event to track all details related to the progression of the user in the game.
 */
class JtProgressionEvent : AppEvent {

    /**
     * Represents progression action.
     */
    enum class Action(
        internal val encodedName: String,
    ) {
        /**
         * Progression action when progression is started.
         */
        START("start"),

        /**
         * Progression action when progression is completed.
         */
        COMPLETE("complete"),

        /**
         * Progression action when progression is failed.
         */
        FAIL("fail"),
    }

    constructor(
        jtAction: Action,
        jtProgression1: String?,
        jtProgression2: String?,
        jtProgression3: String?,
    ) : this(jtAction.encodedName, jtProgression1, jtProgression2, jtProgression3)

    constructor(
        jtAction: Action,
        jtProgression1: String?,
        jtProgression2: String?,
        jtProgression3: String?,
        duration: Double,
        unit: TimeUnitGroup,
    ) : this(jtAction.encodedName, jtProgression1, jtProgression2, jtProgression3, duration, unit)

    constructor(
        jtAction: String,
        jtProgression1: String?,
        jtProgression2: String?,
        jtProgression3: String?,
        duration: Double,
        unit: TimeUnitGroup,
    ) : super(
        NAME,
    ) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_PROGRESSION_1, jtProgression1)
        addDimension(Dimension.JT_PROGRESSION_2, jtProgression2)
        addDimension(Dimension.JT_PROGRESSION_3, jtProgression3)
        setValue(duration, unit.base)
    }

    constructor(jtAction: String, jtProgression1: String?, jtProgression2: String?, jtProgression3: String?) : super(NAME) {
        addDimension(Dimension.JT_ACTION, jtAction)
        addDimension(Dimension.JT_PROGRESSION_1, jtProgression1)
        addDimension(Dimension.JT_PROGRESSION_2, jtProgression2)
        addDimension(Dimension.JT_PROGRESSION_3, jtProgression3)
    }

    /** Constants for [JtProgressionEvent]. */
    companion object {
        /** The canonical event name sent to the backend. */
        const val NAME: String = "jt_progression"
    }
}
