package io.justtrack

internal data class DefaultEntities<T>(
    val processingData: T,
    val firstAvailableData: T,
    val secondAvailableData: T,
    val thirdAvailableData: T,
)
