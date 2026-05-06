package io.justtrack

internal class RetryConfig(
    val attributionRequestRetries: Int,
    val fetchClaimRetries: Int,
    val publishEventsRetries: Int,
    val integrityTokenRetries: List<Int>,
) {
    companion object {
        @JvmStatic val DEFAULT_INTEGRITY_CONFIG: List<Int> = listOf(5, 10, 10, 10)

        @JvmStatic val TEST_INTEGRITY_CONFIG: List<Int> = listOf(1, 1, 1)

        @JvmStatic @JvmSynthetic
        val DEFAULT_CONFIG: RetryConfig = RetryConfig(
            5,
            5,
            5,
            DEFAULT_INTEGRITY_CONFIG,
        )
    }
}
