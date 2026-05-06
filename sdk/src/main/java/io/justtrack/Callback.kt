package io.justtrack

/**
 * A callback represents a computation which is awaiting some data to resolve to a result or
 * be rejected with some error.
 */
interface Callback<T> {
    /**
     * Called after the operation was successful.
     *
     * @param response The data the operation produced.
     */
    fun resolve(response: T)

    /**
     * Called in case an error which can noy be handled occurs. If this method is called, [.resolve]
     * will not be called anymore.
     *
     * @param exception The error which occurred.
     */
    fun reject(exception: Throwable)
}
