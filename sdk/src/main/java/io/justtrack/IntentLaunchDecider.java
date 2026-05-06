package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.justtrack.retargeting.PreliminaryRetargetingParameters;

class IntentLaunchDecider implements ReAttributionDecider {
    private final boolean needsReAttribution;

    IntentLaunchDecider(@Nullable PreliminaryRetargetingParameters parameters) {
        needsReAttribution = parameters != null;
    }

    @NonNull
    @Override
    public AttributionDecision needsReAttribution(@Nullable AttributionTimestamps attributionTimestamps) {
        return needsReAttribution ? AttributionDecision.FETCH_RETARGETING_ATTRIBUTION : AttributionDecision.USE_STORED_ATTRIBUTION;
    }
}
