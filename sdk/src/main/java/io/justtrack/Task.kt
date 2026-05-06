package io.justtrack

internal fun interface Task<T> {
    suspend fun execute(): T
}
