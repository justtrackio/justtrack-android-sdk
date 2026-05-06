package io.justtrack

import java.util.Date

internal class AppStartDuration private constructor(
    private val startedAt: Date,
    private val completionDuration: Double,
) {

    internal constructor(startedAtMillis: Long) : this(
        Date(startedAtMillis),
        (System.currentTimeMillis() - startedAtMillis).toDouble(),
    )

    fun getStartedAt(): Date = startedAt

    fun getCompletionDuration(): Double = completionDuration
}
