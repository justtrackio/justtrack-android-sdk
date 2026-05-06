package io.justtrack;

import androidx.annotation.NonNull;

interface ClaimProvider {
    @Hidden
    long CLAIM_TIMEOUT_FAST_MS = 750;
    @Hidden
    long CLAIM_TIMEOUT_SLOW_MS = 60_000;

    void refreshClaims(@NonNull BaseJustTrackSdk sdk);

    @NonNull
    ProvidedClaims provideClaims(long timeout);
}
