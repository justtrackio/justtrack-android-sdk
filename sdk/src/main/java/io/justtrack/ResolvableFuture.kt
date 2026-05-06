package io.justtrack

import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class ResolvableFuture<V> : ResolvableFutureBase<V>() {
    override suspend fun await(): V = suspendCoroutine { continuation ->
        val continued = AtomicBoolean()

        registerCallback(
            object : Callback<V> {
                override fun resolve(response: V) {
                    if (!continued.getAndSet(true)) {
                        continuation.resume(response)
                    }
                }

                override fun reject(exception: Throwable) {
                    if (!continued.getAndSet(true)) {
                        continuation.resumeWith(Result.failure(exception))
                    }
                }
            },
        )
    }

    override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): V? = withTimeoutOrNull(timeUnit.toMillis(timeout)) {
        await()
    }
}
