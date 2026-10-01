package io.justtrack

import io.justtrack.exceptions.IntegrityException

internal data class IntegrityTokenData(
    val previouslySent: Boolean = false,
    val token: String? = null,
    val integrityException: IntegrityException? = null,
    val generatedTimestamp: Long = System.currentTimeMillis(),
)
