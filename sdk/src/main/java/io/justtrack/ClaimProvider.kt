package io.justtrack

internal interface ClaimProvider {
    fun refreshClaims()
    fun provideClaims(timeout: Long): ProvidedClaims
}
