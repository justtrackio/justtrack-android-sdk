package io.justtrack;

import androidx.annotation.NonNull;

class AttributionDecision {
    static final @NonNull AttributionDecision FETCH_FIRST_ATTRIBUTION = new AttributionDecision(
            ClaimProvider.CLAIM_TIMEOUT_FAST_MS,
            false,
            false,
            true
    );
    static final @NonNull AttributionDecision FETCH_RETARGETING_ATTRIBUTION = new AttributionDecision(
            ClaimProvider.CLAIM_TIMEOUT_FAST_MS,
            true,
            false,
            true
    );
    static final @NonNull AttributionDecision FETCH_RETARGETING_ATTRIBUTION_DELAYED = new AttributionDecision(
            ClaimProvider.CLAIM_TIMEOUT_FAST_MS,
            true,
            true,
            true
    );
    static final @NonNull AttributionDecision USE_STORED_ATTRIBUTION = new AttributionDecision(
            ClaimProvider.CLAIM_TIMEOUT_FAST_MS,
            false,
            false,
            false
    );

    private final long claimsTimeout;
    private final boolean isRetargetingAttribution;
    private final boolean isRefresh;
    private final boolean shouldFetchAttribution;

    private AttributionDecision(long claimsTimeout, boolean isRetargetingAttribution, boolean isRefresh, boolean shouldFetchAttribution) {
        this.claimsTimeout = claimsTimeout;
        this.isRetargetingAttribution = isRetargetingAttribution;
        this.isRefresh = isRefresh;
        this.shouldFetchAttribution = shouldFetchAttribution;
    }

    @NonNull
    AttributionDecision withSlowClaimTimeout() {
        return new AttributionDecision(
                ClaimProvider.CLAIM_TIMEOUT_SLOW_MS,
                isRetargetingAttribution,
                isRefresh,
                true
        );
    }

    boolean shouldFetchAttribution() {
        return shouldFetchAttribution;
    }

    boolean isFetchRetargetingAttribution() {
        return isRetargetingAttribution && !isRefresh;
    }

    boolean shouldUseReferrerDetails(boolean hasStoredResponse) {
        return !isRetargetingAttribution || (!isRefresh && !hasStoredResponse);
    }

    long getClaimsTimeout() {
        return claimsTimeout;
    }

    boolean isFastClaimsTimeout() {
        return claimsTimeout == ClaimProviderImpl.CLAIM_TIMEOUT_FAST_MS;
    }

    @NonNull
    AttributionDecision merge(@NonNull AttributionDecision other) {
        return new AttributionDecision(
                Math.max(claimsTimeout, other.claimsTimeout),
                shouldFetchAttribution
                        ? isRetargetingAttribution && (!other.shouldFetchAttribution || other.isRetargetingAttribution)
                        : other.isRetargetingAttribution,
                isRefresh && other.isRefresh,
                shouldFetchAttribution || other.shouldFetchAttribution
        );
    }

    @Override
    @NonNull
    public String toString() {
        return "AttributionDecision{"
                + "claimsTimeout="
                + claimsTimeout
                + ", isRetargetingAttribution="
                + isRetargetingAttribution
                + ", isRefresh=" + isRefresh
                + ", shouldFetchAttribution="
                + shouldFetchAttribution
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AttributionDecision)) {
            return false;
        }

        AttributionDecision that = (AttributionDecision) object;

        if (claimsTimeout != that.claimsTimeout) {
            return false;
        }
        if (isRetargetingAttribution != that.isRetargetingAttribution) {
            return false;
        }
        if (isRefresh != that.isRefresh) {
            return false;
        }
        return shouldFetchAttribution == that.shouldFetchAttribution;
    }

    @Override
    public int hashCode() {
        int result = (int) (claimsTimeout ^ (claimsTimeout >>> 32));
        result = 31 * result + (isRetargetingAttribution ? 1 : 0);
        result = 31 * result + (isRefresh ? 1 : 0);
        result = 31 * result + (shouldFetchAttribution ? 1 : 0);

        return result;
    }
}
