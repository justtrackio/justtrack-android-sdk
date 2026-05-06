package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

import io.justtrack.attribution.Attribution;
import io.justtrack.attribution.Campaign;
import io.justtrack.attribution.Channel;
import io.justtrack.attribution.IdString;
import io.justtrack.attribution.Partner;

class AttributionImpl implements Attribution {
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

    AttributionImpl(@NonNull AttributionResponse attributionResponse) {
        this.userType = attributionResponse.getUserType();
        this.campaign = attributionResponse.getCampaign();
        this.type = attributionResponse.getType();
        this.channel = attributionResponse.getChannel();
        this.partner = attributionResponse.getPartner();
        this.sourceId = attributionResponse.getSourceId();
        this.sourceBundleId = attributionResponse.getSourceBundleId();
        this.sourcePlacement = attributionResponse.getSourcePlacement();
        this.adsetId = attributionResponse.getAdsetId();
        this.createdAt = attributionResponse.getCreatedAt();
        this.redownload = attributionResponse.getRedownload();
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
    public boolean isRedownload() {
        return redownload;
    }

    static class IdStringImpl implements IdString {
        private final int id;
        @NonNull
        private final String name;

        IdStringImpl(int id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public int getId() {
            return id;
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object compare) {
            if (this == compare) {
                return true;
            }
            if (!(compare instanceof IdStringImpl)) {
                return false;
            }
            IdStringImpl idString = (IdStringImpl) compare;
            return id == idString.id && name.equals(idString.name);
        }

        @Override
        public int hashCode() {
            return id * 31 + name.hashCode();
        }
    }

    static class CampaignImpl extends IdStringImpl implements Campaign {
        @NonNull
        private final String type;
        private final boolean organic;

        CampaignImpl(int id, @NonNull String name, @NonNull String type, boolean organic) {
            super(id, name);
            this.type = type;
            this.organic = organic;
        }

        @NonNull
        @Override
        public String getType() {
            return type;
        }

        @Override
        public boolean isOrganic() {
            return organic;
        }

        @Override
        public boolean equals(Object compareObject) {
            if (this == compareObject) {
                return true;
            }
            if (!(compareObject instanceof CampaignImpl)) {
                return false;
            }
            if (!super.equals(compareObject)) {
                return false;
            }
            CampaignImpl campaign = (CampaignImpl) compareObject;
            return type.equals(campaign.type) && organic == campaign.organic;
        }

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = hash * 31 + type.hashCode();

            return hash * 31 + Boolean.hashCode(organic);
        }
    }

    static class ChannelImpl extends IdStringImpl implements Channel {
        private final boolean incent;

        ChannelImpl(int id, @NonNull String name, boolean incent) {
            super(id, name);
            this.incent = incent;
        }

        @Override
        public boolean isIncent() {
            return incent;
        }

        @Override
        public boolean equals(Object compareObject) {
            if (this == compareObject) {
                return true;
            }
            if (!(compareObject instanceof ChannelImpl)) {
                return false;
            }
            if (!super.equals(compareObject)) {
                return false;
            }
            ChannelImpl channel = (ChannelImpl) compareObject;
            return incent == channel.incent;
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 31 + (incent ? 1231 : 1237);
        }
    }

    static class PartnerImpl extends IdStringImpl implements Partner {
        PartnerImpl(int id, @NonNull String name) {
            super(id, name);
        }
    }

    @Override
    public boolean equals(Object compareObject) {
        if (this == compareObject) {
            return true;
        }
        if (compareObject == null || getClass() != compareObject.getClass()) {
            return false;
        }
        AttributionImpl that = (AttributionImpl) compareObject;
        return userType.equals(that.userType)
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
        int hash = userType.hashCode();
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
