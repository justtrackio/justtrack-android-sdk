package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class ChainedReAttributionDecider implements ReAttributionDecider {
    private final ReAttributionDecider[] deciders;

    ChainedReAttributionDecider(ReAttributionDecider... deciders) {
        this.deciders = deciders;
    }

    @NonNull
    @Override
    public AttributionDecision needsReAttribution(@Nullable AttributionTimestamps attributionTimestamps) {
        AttributionDecision result = AttributionDecision.USE_STORED_ATTRIBUTION;

        for (ReAttributionDecider decider : deciders) {
            result = result.merge(decider.needsReAttribution(attributionTimestamps));
        }

        return result;
    }
}
