package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

interface ReAttributionDecider {
    @NonNull
    AttributionDecision needsReAttribution(@Nullable AttributionTimestamps attributionTimestamps);
}
