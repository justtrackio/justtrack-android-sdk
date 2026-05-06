package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.UUID;

import io.justtrack.attribution.Campaign;
import io.justtrack.attribution.Channel;
import io.justtrack.attribution.Partner;

class AttributionResponseImpl implements AttributionResponse {
    @NonNull
    private final UUID userId;
    @NonNull
    private final String installId;
    @NonNull
    private final String userType;
    @NonNull
    private final Campaign campaign;
    @NonNull
    private final String type;
    @NonNull
    private final Channel channel;
    @NonNull
    private final Partner partner;
    @Nullable
    private final String sourceId;
    @Nullable
    private final String sourceBundleId;
    @Nullable
    private final String sourcePlacement;
    @Nullable
    private final String adsetId;
    @NonNull
    private final Date createdAt;
    private final boolean redownload;

    AttributionResponseImpl(
            @NonNull UUID userId,
            @NonNull String installId,
            @NonNull String userType,
            @NonNull Campaign campaign,
            @NonNull String type,
            @NonNull Channel channel,
            @NonNull Partner partner,
            @Nullable String sourceId,
            @Nullable String sourceBundleId,
            @Nullable String sourcePlacement,
            @Nullable String adsetId,
            @NonNull Date createdAt,
            boolean redownload
    ) {
        this.userId = userId;
        this.installId = installId;
        this.userType = userType;
        this.campaign = campaign;
        this.type = type;
        this.channel = channel;
        this.partner = partner;
        this.sourceId = sourceId;
        this.sourceBundleId = sourceBundleId;
        this.sourcePlacement = sourcePlacement;
        this.adsetId = adsetId;
        this.createdAt = createdAt;
        this.redownload = redownload;
    }

    @NonNull
    @Override
    public UUID getUserId() {
        return userId;
    }

    @NonNull
    @Override
    public String getInstallId() {
        return installId;
    }

    @NonNull
    @Override
    public String getUserType() {
        return userType;
    }

    @NonNull
    @Override
    public Campaign getCampaign() {
        return campaign;
    }

    @NonNull
    @Override
    public String getType() {
        return type;
    }

    @NonNull
    @Override
    public Channel getChannel() {
        return channel;
    }

    @NonNull
    @Override
    public Partner getPartner() {
        return partner;
    }

    @Nullable
    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Nullable
    @Override
    public String getSourceBundleId() {
        return sourceBundleId;
    }

    @Nullable
    @Override
    public String getSourcePlacement() {
        return sourcePlacement;
    }

    @Nullable
    @Override
    public String getAdsetId() {
        return adsetId;
    }

    @NonNull
    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean getRedownload() {
        return redownload;
    }

    @Override
    public boolean equals(Object compareObject) {
        if (this == compareObject) {
            return true;
        }
        if (compareObject == null || getClass() != compareObject.getClass()) {
            return false;
        }
        AttributionResponseImpl that = (AttributionResponseImpl) compareObject;
        return userId.equals(that.userId)
                && installId.equals(that.installId)
                && userType.equals(that.userType)
                && campaign.equals(that.campaign)
                && type.equals(that.type)
                && channel.equals(that.channel)
                && partner.equals(that.partner)
                && equals(sourceId, that.sourceId)
                && equals(sourceBundleId, that.sourceBundleId)
                && equals(sourcePlacement, that.sourcePlacement)
                && equals(adsetId, that.adsetId)
                && createdAt.equals(that.createdAt)
                && redownload == that.redownload;
    }

    // Objects.Equals replacement for API level < 19
    private static boolean equals(Object a, Object b) {
        //noinspection EqualsReplaceableByObjectsCall
        return (a == b) || (a != null && a.equals(b));
    }

    @Override
    public int hashCode() {
        int hash = userId.hashCode();
        hash = hash * 31 + installId.hashCode();
        hash = hash * 31 + userType.hashCode();
        hash = hash * 31 + campaign.hashCode();
        hash = hash * 31 + type.hashCode();
        hash = hash * 31 + channel.hashCode();
        hash = hash * 31 + partner.hashCode();
        if (sourceId != null) {
            hash = hash * 31 + sourceId.hashCode();
        }
        if (sourceBundleId != null) {
            hash = hash * 31 + sourceBundleId.hashCode();
        }
        if (sourcePlacement != null) {
            hash = hash * 31 + sourcePlacement.hashCode();
        }
        if (adsetId != null) {
            hash = hash * 31 + adsetId.hashCode();
        }
        hash = hash * 31 + createdAt.hashCode();

        return hash * 31 + (redownload ? 1 : 0);
    }
}
