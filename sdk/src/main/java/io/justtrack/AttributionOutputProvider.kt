package io.justtrack

internal interface AttributionOutputProvider {
    fun setOutput(output: AsyncFuture<AttributionOutput>?)

    fun getOutput(): AsyncFuture<AttributionOutput>?

    fun setAttributionCanRetryAt(value: Long)

    fun getLatestApiAttributionOutput(): AsyncFuture<AttributionOutput>?

    fun getReFetchReAttributionDelaySeconds(): Long

    fun getAttributionRetryDelaySeconds(): Long

    fun provideAttributionOutput(forcedDecision: AttributionDecision? = null): AsyncFuture<AttributionOutput>
}
