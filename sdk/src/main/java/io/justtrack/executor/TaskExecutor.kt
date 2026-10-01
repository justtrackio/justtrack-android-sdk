package io.justtrack.executor

import io.justtrack.AsyncFuture
import io.justtrack.Callback
import io.justtrack.RejectedExecutionExceptionHandler
import io.justtrack.Task
import java.util.concurrent.Future

/**
 * Full task executor for the SDK, extending [SyncTaskExecutor] with the ability to run
 * [Task] instances and expose their results as [Future] or [AsyncFuture].
 *
 * All task execution is routed through the SDK's managed thread pool. If the pool rejects a
 * task (e.g. after [io.justtrack.JustTrackSdk.shutdown] is called), the returned future is
 * cancelled immediately so that any caller blocked on [Future.get] or [AsyncFuture.await]
 * receives a [java.util.concurrent.CancellationException] rather than hanging indefinitely.
 */
internal interface TaskExecutor {
    /**
     * Executes [task] on the managed thread pool and returns an [AsyncFuture] that resolves
     * to the task's result.
     *
     * The [AsyncFuture] can be awaited in a coroutine via [AsyncFuture.await] or consumed
     * via a callback using [AsyncFuture.registerCallback].
     *
     * If the thread pool rejects the task, the returned [AsyncFuture] is cancelled and any
     * invoker receives a [java.util.concurrent.CancellationException].
     *
     * @param task The task to execute.
     * @return An [AsyncFuture] that resolves to the task's result.
     */
    fun <V> executeFuture(task: Task<V>): AsyncFuture<V>

    /**
     * Submits [task] for execution on the managed thread pool.
     *
     * If the thread pool is saturated or has been shut down, [rejectedHandler] is invoked
     * instead of executing the task.
     *
     * @param task            The runnable to execute.
     * @param rejectedHandler Called if the task cannot be accepted by the thread pool.
     */
    fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler)

    /**
     * Submits [task] for execution, with explicit control over whether the serial-execution
     * mode configured via [io.justtrack.JustTrackSdkBuilder.setRunCallbackSerially] is honoured.
     *
     * When [io.justtrack.JustTrackSdkBuilder.setRunCallbackSerially] is set to `true`, all
     * tasks are normally dispatched to a dedicated serial handler thread to ensure callbacks
     * are delivered one-at-a-time in order. Passing `ignoreSerially = true` bypasses this
     * behaviour and submits the task directly to the thread pool regardless of that setting.
     *
     * **This overload should only be used when you explicitly need the task to run serially
     * (i.e. through the serial handler thread) and `runCallbackSerially` may be `true`.**
     * For the common case where serial ordering is not required, prefer
     * [execute(Runnable, RejectedExecutionExceptionHandler)], which always ignores the serial
     * setting and submits directly to the thread pool.
     *
     * @param task             The runnable to execute.
     * @param rejectedHandler  Called if the task cannot be accepted by the thread pool or
     *                         serial handler thread.
     * @param ignoreSerially   If `false`, the task is routed through the serial handler thread
     *                         when [io.justtrack.JustTrackSdkBuilder.setRunCallbackSerially]
     *                         is `true`. If `true`, the serial setting is ignored and the task
     *                         is submitted directly to the thread pool.
     */
    fun execute(task: Runnable, rejectedHandler: RejectedExecutionExceptionHandler, ignoreSerially: Boolean)

    /**
     * Wraps [callback] in a new [Callback] whose [Callback.resolve] and [Callback.reject]
     * invocations are dispatched through the executor, ensuring the result is delivered on
     * the correct thread.
     *
     * When [io.justtrack.JustTrackSdkBuilder.setRunCallbackSerially] is set to `true`, the
     * wrapped callback's result will be delivered serially via the dedicated serial handler
     * thread, guaranteeing that callbacks are invoked one-at-a-time in order. Otherwise,
     * the result is delivered on the managed thread pool as normal.
     *
     * @param callback The callback to wrap.
     * @return A new [Callback] that dispatches [Callback.resolve] and [Callback.reject]
     *         through the executor, respecting the serial delivery configuration.
     */
    fun <V> wrap(callback: Callback<V>): Callback<V>
}
