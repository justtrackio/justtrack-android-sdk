package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class ResolveOrganicAttributionDecider implements ReAttributionDecider {
    private static final long OLD_ATTRIBUTION_AGE = 15 * 60 * 1000;

    ResolveOrganicAttributionDecider() {
    }

    @NonNull
    @Override
    public AttributionDecision needsReAttribution(@Nullable AttributionTimestamps attributionTimestamps) {
        if (attributionTimestamps == null) {
            return AttributionDecision.FETCH_FIRST_ATTRIBUTION;
        }

        long attributionAge = attributionTimestamps.getLastAttributionAt() - attributionTimestamps.getFirstAttributionAt();

        // we need to attribute a user again (because a new postback could have arrived) should
        // the last attribution we have (if any) be not older than 15 minutes of the first attribution
        // we performed
        return attributionAge <= OLD_ATTRIBUTION_AGE ? AttributionDecision.FETCH_FIRST_ATTRIBUTION : AttributionDecision.USE_STORED_ATTRIBUTION;
    }
}
