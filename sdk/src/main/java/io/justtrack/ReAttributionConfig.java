package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class ReAttributionConfig implements ReAttributionDecider {
    private long inactivityTimeFrameHours;
    private long reAttributionTimeFrameDays;
    private long reFetchReAttributionDelaySeconds;

    ReAttributionConfig() {
        this.inactivityTimeFrameHours = 48;
        this.reAttributionTimeFrameDays = 14;
        this.reFetchReAttributionDelaySeconds = 5;
    }

    void setInactivityTimeFrameHours(long inactivityTimeFrameHours) {
        this.inactivityTimeFrameHours = inactivityTimeFrameHours;
    }

    void setReAttributionTimeFrameDays(long reAttributionTimeFrameDays) {
        this.reAttributionTimeFrameDays = reAttributionTimeFrameDays;
    }

    long getReFetchReAttributionDelaySeconds() {
        return reFetchReAttributionDelaySeconds;
    }

    void setReFetchReAttributionDelaySeconds(long reFetchReAttributionDelaySeconds) {
        this.reFetchReAttributionDelaySeconds = reFetchReAttributionDelaySeconds;
    }

    @NonNull
    @Override
    public AttributionDecision needsReAttribution(@Nullable AttributionTimestamps attributionTimestamps) {
        if (attributionTimestamps == null) {
            // user was never attributed before
            return AttributionDecision.FETCH_FIRST_ATTRIBUTION;
        }

        long now = System.currentTimeMillis();
        long attributeAfterAppOpen = attributionTimestamps.getLastOpenAt() + 1000 * 3600 * inactivityTimeFrameHours;
        long attributeAfterAttribution = attributionTimestamps.getLastAttributionAt() + 1000 * 3600 * 24 * reAttributionTimeFrameDays;

        return now >= attributeAfterAppOpen || now >= attributeAfterAttribution
                ? AttributionDecision.FETCH_RETARGETING_ATTRIBUTION : AttributionDecision.USE_STORED_ATTRIBUTION;
    }
}
