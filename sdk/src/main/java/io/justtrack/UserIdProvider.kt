package io.justtrack

internal fun interface UserIdProvider {
    fun provideUserIdFuture(): AsyncFuture<String>
}
