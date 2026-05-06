package io.justtrack

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Future which wraps another future and transforms the result before returning.
 *
 * @param <A> Type the wrapped future resolves to.
 * @param <B> Type the transformed future resolves to.
 */
internal class TransformingFuture<A, B>(
    private val wrapped: AsyncFuture<A>,
    private val transformer: Transformer<A, B>,
) : AsyncFuture<B> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return wrapped.cancel(mayInterruptIfRunning)
    }

    override fun isCancelled(): Boolean {
        return wrapped.isCancelled
    }

    override fun isDone(): Boolean {
        return wrapped.isDone
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun get(): B {
        return transformer.transform(wrapped.get())
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override fun get(timeout: Long, unit: TimeUnit): B {
        return transformer.transform(wrapped[timeout, unit])
    }

    override suspend fun await(): B {
        return transformer.transform(wrapped.await())
    }

    override suspend fun awaitOrNull(timeout: Long, timeUnit: TimeUnit): B? {
        val result = wrapped.awaitOrNull(timeout, timeUnit)
        if (result != null) {
            return transformer.transform(result)
        }

        return null
    }

    override fun registerCallback(callback: Callback<B>) {
        wrapped.registerCallback(
            object : Callback<A> {
                override fun resolve(response: A) {
                    callback.resolve(transformer.transform(response))
                }

                override fun reject(exception: Throwable) {
                    callback.reject(exception)
                }
            },
        )
    }
}
