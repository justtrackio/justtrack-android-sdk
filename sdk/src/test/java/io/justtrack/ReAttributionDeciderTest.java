package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Assert;
import org.junit.Test;

public class ReAttributionDeciderTest {
    @Test
    public void checkAttributionDecisions() {
        Assert.assertTrue(AttributionDecision.FETCH_FIRST_ATTRIBUTION.getShouldFetchAttribution());
        Assert.assertTrue(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION.getShouldFetchAttribution());
        Assert.assertTrue(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED.getShouldFetchAttribution());
        Assert.assertFalse(AttributionDecision.USE_STORED_ATTRIBUTION.getShouldFetchAttribution());

        Assert.assertFalse(AttributionDecision.FETCH_FIRST_ATTRIBUTION.isFetchRetargetingAttribution());
        Assert.assertTrue(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION.isFetchRetargetingAttribution());
        Assert.assertFalse(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED.isFetchRetargetingAttribution());
        Assert.assertFalse(AttributionDecision.USE_STORED_ATTRIBUTION.isFetchRetargetingAttribution());

        Assert.assertTrue(AttributionDecision.FETCH_FIRST_ATTRIBUTION.shouldUseReferrerDetails(false));
        Assert.assertTrue(AttributionDecision.FETCH_FIRST_ATTRIBUTION.shouldUseReferrerDetails(true));
        Assert.assertTrue(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION.shouldUseReferrerDetails(false));
        Assert.assertFalse(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION.shouldUseReferrerDetails(true));
        Assert.assertFalse(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED.shouldUseReferrerDetails(false));
        Assert.assertFalse(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED.shouldUseReferrerDetails(true));
        Assert.assertTrue(AttributionDecision.USE_STORED_ATTRIBUTION.shouldUseReferrerDetails(false));
        Assert.assertTrue(AttributionDecision.USE_STORED_ATTRIBUTION.shouldUseReferrerDetails(true));
    }

    @Test
    public void reAttributionWithNulls() {
        ReAttributionConfig config = new ReAttributionConfig();
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, config.needsReAttribution(null));
        ResolveOrganicAttributionDecider organicDecider = new ResolveOrganicAttributionDecider();
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, organicDecider.needsReAttribution(null));
        ChainedReAttributionDecider chainedDecider = new ChainedReAttributionDecider(config, organicDecider);
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        chainedDecider = new ChainedReAttributionDecider(organicDecider, config);
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        chainedDecider = new ChainedReAttributionDecider(organicDecider);
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        chainedDecider = new ChainedReAttributionDecider(config);
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        chainedDecider = new ChainedReAttributionDecider();
        Assert.assertEquals(AttributionDecision.USE_STORED_ATTRIBUTION, chainedDecider.needsReAttribution(null));
    }

    @Test
    public void chainReAttribution() {
        ConstantReAttributionDecider useStoredAttributionDecider = new ConstantReAttributionDecider(AttributionDecision.USE_STORED_ATTRIBUTION);
        ConstantReAttributionDecider fetchFirstAttributionDecider = new ConstantReAttributionDecider(AttributionDecision.FETCH_FIRST_ATTRIBUTION);
        ChainedReAttributionDecider chainedDecider = new ChainedReAttributionDecider(useStoredAttributionDecider, fetchFirstAttributionDecider);
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        Assert.assertEquals(useStoredAttributionDecider.calls, 1);
        Assert.assertEquals(fetchFirstAttributionDecider.calls, 1);
        chainedDecider = new ChainedReAttributionDecider(fetchFirstAttributionDecider, useStoredAttributionDecider);
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        Assert.assertEquals(useStoredAttributionDecider.calls, 2);
        Assert.assertEquals(fetchFirstAttributionDecider.calls, 2);
        chainedDecider = new ChainedReAttributionDecider(fetchFirstAttributionDecider);
        Assert.assertEquals(AttributionDecision.FETCH_FIRST_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        Assert.assertEquals(useStoredAttributionDecider.calls, 2);
        Assert.assertEquals(fetchFirstAttributionDecider.calls, 3);
        chainedDecider = new ChainedReAttributionDecider(useStoredAttributionDecider);
        Assert.assertEquals(AttributionDecision.USE_STORED_ATTRIBUTION, chainedDecider.needsReAttribution(null));
        Assert.assertEquals(useStoredAttributionDecider.calls, 3);
        Assert.assertEquals(fetchFirstAttributionDecider.calls, 3);
    }

    @Test
    public void reAttributionAfterOrganicExpire() {
        ResolveOrganicAttributionDecider decider = new ResolveOrganicAttributionDecider();
        long now = System.currentTimeMillis();
        long firstAttributionAt = now - 1000 * 60;
        Assert.assertEquals(
                AttributionDecision.FETCH_FIRST_ATTRIBUTION,
                decider.needsReAttribution(new AttributionTimestamps(firstAttributionAt, now, now))
        );
        firstAttributionAt = now - 1000 * 60 * 20;
        Assert.assertEquals(
                AttributionDecision.USE_STORED_ATTRIBUTION,
                decider.needsReAttribution(new AttributionTimestamps(firstAttributionAt, now, now))
        );
    }

    @Test
    public void reAttributionAfterAppOpen() {
        ReAttributionConfig config = new ReAttributionConfig();
        config.setInactivityTimeFrameHours(5);
        long now = System.currentTimeMillis();
        long firstAttributionAt = now - 1000 * 3600 * 24;
        long lastAttributionAt = now - 1000 * 3600 * 12;
        long lastOpenAt = now - 6 * 1000 * 3600;
        Assert.assertEquals(
                AttributionDecision.FETCH_RETARGETING_ATTRIBUTION,
                config.needsReAttribution(new AttributionTimestamps(firstAttributionAt, lastAttributionAt, lastOpenAt))
        );
        lastOpenAt = now - 4 * 1000 * 3600;
        Assert.assertEquals(
                AttributionDecision.USE_STORED_ATTRIBUTION,
                config.needsReAttribution(new AttributionTimestamps(firstAttributionAt, lastAttributionAt, lastOpenAt))
        );
    }

    @Test
    public void reAttributionAfterAttribution() {
        ReAttributionConfig config = new ReAttributionConfig();
        config.setReAttributionTimeFrameDays(3);
        long now = System.currentTimeMillis();
        long firstAttributionAt = now - 1000 * 3600 * 24 * 5;
        long lastAttributionAt = now - 1000 * 3600 * 24 * 4;
        Assert.assertEquals(
                AttributionDecision.FETCH_RETARGETING_ATTRIBUTION,
                config.needsReAttribution(new AttributionTimestamps(firstAttributionAt, lastAttributionAt, now))
        );
        lastAttributionAt = now - 1000 * 3600 * 24 * 2;
        Assert.assertEquals(
                AttributionDecision.USE_STORED_ATTRIBUTION,
                config.needsReAttribution(new AttributionTimestamps(firstAttributionAt, lastAttributionAt, now))
        );
    }

    private static class ConstantReAttributionDecider implements ReAttributionDecider {
        private final AttributionDecision value;
        private int calls;

        private ConstantReAttributionDecider(AttributionDecision value) {
            this.value = value;
            this.calls = 0;
        }

        @NonNull
        @Override
        public AttributionDecision needsReAttribution(@Nullable AttributionTimestamps attributionTimestamps) {
            this.calls++;

            return value;
        }
    }
}
