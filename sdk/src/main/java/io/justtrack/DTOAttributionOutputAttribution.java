package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;

class DTOAttributionOutputAttribution {
    @NonNull
    private final DTOAttributionOutputAttributionCampaign campaign;
    @NonNull
    private final String type;
    @NonNull
    private final DTOAttributionOutputAttributionChannel channel;
    @NonNull
    private final DTOAttributionOutputAttributionNetwork network;
    @Nullable
    private final String sourceId;
    @Nullable
    private final String sourceBundleId;
    @Nullable
    private final String sourcePlacement;
    @Nullable
    private final String adsetId;
    @NonNull
    private final Date attributedAt;

    DTOAttributionOutputAttribution(@NonNull DTOAttributionOutputAttribution obj) {
        this.campaign = obj.campaign;
        this.type = obj.type;
        this.channel = obj.channel;
        this.network = obj.network;
        this.sourceId = obj.sourceId;
        this.sourceBundleId = obj.sourceBundleId;
        this.sourcePlacement = obj.sourcePlacement;
        this.adsetId = obj.adsetId;
        this.attributedAt = obj.attributedAt;
    }

    DTOAttributionOutputAttribution(
            @NonNull DTOAttributionOutputAttributionCampaign campaign,
            @NonNull String type,
            @NonNull DTOAttributionOutputAttributionChannel channel,
            @NonNull DTOAttributionOutputAttributionNetwork network,
            @Nullable String sourceId,
            @Nullable String sourceBundleId,
            @Nullable String sourcePlacement,
            @Nullable String adsetId,
            @NonNull Date attributedAt) {
        this.campaign = campaign;
        this.type = type;
        this.channel = channel;
        this.network = network;
        this.sourceId = sourceId;
        this.sourceBundleId = sourceBundleId;
        this.sourcePlacement = sourcePlacement;
        this.adsetId = adsetId;
        this.attributedAt = attributedAt;
    }

    DTOAttributionOutputAttribution(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.campaign = new DTOAttributionOutputAttributionCampaign(obj.getJSONObject("campaign"));
        this.type = obj.getString("type");
        this.channel = new DTOAttributionOutputAttributionChannel(obj.getJSONObject("channel"));
        this.network = new DTOAttributionOutputAttributionNetwork(obj.getJSONObject("network"));

        if (obj.has("sourceId") && obj.get("sourceId") != JSONObject.NULL) {
            this.sourceId = obj.getString("sourceId");
        } else {
            this.sourceId = null;
        }

        if (obj.has("sourceBundleId") && obj.get("sourceBundleId") != JSONObject.NULL) {
            this.sourceBundleId = obj.getString("sourceBundleId");
        } else {
            this.sourceBundleId = null;
        }

        if (obj.has("sourcePlacement") && obj.get("sourcePlacement") != JSONObject.NULL) {
            this.sourcePlacement = obj.getString("sourcePlacement");
        } else {
            this.sourcePlacement = null;
        }

        if (obj.has("adsetId") && obj.get("adsetId") != JSONObject.NULL) {
            this.adsetId = obj.getString("adsetId");
        } else {
            this.adsetId = null;
        }
        this.attributedAt = formatter.parseDate(obj.getString("attributedAt"));
    }

    @NonNull
    DTOAttributionOutputAttributionCampaign getCampaign() {
        return this.campaign;
    }

    @NonNull
    String getType() {
        return this.type;
    }

    @NonNull
    DTOAttributionOutputAttributionChannel getChannel() {
        return this.channel;
    }

    @NonNull
    DTOAttributionOutputAttributionNetwork getNetwork() {
        return this.network;
    }

    @Nullable
    String getSourceId() {
        return this.sourceId;
    }

    @Nullable
    String getSourceBundleId() {
        return this.sourceBundleId;
    }

    @Nullable
    String getSourcePlacement() {
        return this.sourcePlacement;
    }

    @Nullable
    String getAdsetId() {
        return this.adsetId;
    }

    @NonNull
    Date getAttributedAt() {
        return this.attributedAt;
    }
}