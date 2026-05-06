package io.justtrack

import io.justtrack.exceptions.IntegrityException

internal class IntegrityTokenData internal constructor(
    val previouslySent: Boolean = false,
    val token: String? = null,
    val integrityException: IntegrityException? = null,
    val generatedTimestamp: Long = System.currentTimeMillis(),
) {
    override fun toString(): String {
        return "IntegrityTokenData(" +
            "previouslySent=$previouslySent, " +
            "token=$token, " +
            "integrityException=$integrityException, " +
            "generatedTimestamp=$generatedTimestamp" +
            ")"
    }
}
