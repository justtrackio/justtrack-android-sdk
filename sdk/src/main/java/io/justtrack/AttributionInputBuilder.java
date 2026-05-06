package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

import io.justtrack.installreferrer.api.ReferrerDetails;
import io.justtrack.versions.SdkVersion;
import io.justtrack.versions.VersionBundle;

class AttributionInputBuilder {
    @NonNull
    private final VersionBundle versionBundle;
    @NonNull
    private final String deviceId;
    @Nullable
    private final String advertiserId;
    @Nullable
    private final String appSetId;
    private final boolean hasLimitedAdTracking;
    @Nullable
    private final String trackingId;
    @NonNull
    private final String trackingProvider;

    @NonNull
    private final Iterable<String> claims;

    @NonNull
    private final String installSource;

    @Nullable
    private final ReferrerDetails referrerDetails;
    @NonNull
    private final Date clientDate;

    @NonNull
    private final String userId;
    @Nullable
    private final String customUserId;
    @NonNull
    private final String installInstanceId;

    @Nullable
    private final String integritySecret;
    @NonNull
    private final DeviceInfo deviceInfo;

    AttributionInputBuilder(
            @NonNull VersionBundle versionBundle,
            @NonNull DeviceInfo deviceInfo,
            @Nullable String advertiserId,
            boolean hasLimitedAdTracking,
            @Nullable String trackingId,
            @NonNull String trackingProvider,
            @NonNull Iterable<String> claims,
            @NonNull String installSource,
            @Nullable ReferrerDetails referrerDetails,
            @Nullable String appSetId,
            @NonNull String userId,
            @NonNull String installInstanceId,
            @Nullable String customUserId,
            @Nullable String integritySecret
    ) {
        this.versionBundle = versionBundle;
        this.deviceId = deviceInfo.getAndroidIdOrDefault("");
        this.advertiserId = advertiserId;
        this.hasLimitedAdTracking = hasLimitedAdTracking;
        this.trackingId = trackingId;
        this.trackingProvider = trackingProvider;

        this.deviceInfo = deviceInfo;

        this.claims = claims;

        this.installSource = installSource;

        this.referrerDetails = referrerDetails;
        clientDate = new Date();
        this.appSetId = appSetId;

        this.userId = userId;
        this.customUserId = customUserId;
        this.installInstanceId = installInstanceId;
        this.integritySecret = integritySecret;
    }

    @NonNull
    DTOAttributionInput build() {
        DTOAttributionInputParameters parameters = new DTOAttributionInputParameters(null, installSource, null, integritySecret);
        SdkVersion sdkVersion = versionBundle.getSdkVersion();
        return new DTOAttributionInput(
                new DTOAppVersion(versionBundle.getApplicationVersion().getVersionName(), versionBundle.getApplicationVersion().getVersionCode()),
                new DTOSdkVersion(
                        sdkVersion.getMajor(),
                        sdkVersion.getMinor(),
                        sdkVersion.getPatch(),
                        sdkVersion.getName(),
                        sdkVersion.getPlatformType().getPlatform(),
                        sdkVersion.getPlatformType().getWrapper()
                ),
                new DTOAttributionInputUser(
                        userId,
                        customUserId,
                        installInstanceId,
                        deviceId,
                        advertiserId,
                        trackingId,
                        trackingProvider,
                        deviceInfo.getCountryIso(),
                        appSetId,
                        hasLimitedAdTracking
                ),
                new DTOAttributionInputDevice(
                        deviceInfo.getDeviceName(),
                        deviceInfo.getDeviceModel(),
                        deviceInfo.getDeviceProduct(),
                        deviceInfo.getDeviceType(),
                        new DTOAttributionInputDeviceOS(deviceInfo.getOsVersion(), deviceInfo.getOsName()),
                        new DTOAttributionInputDeviceDisplay(deviceInfo.getDisplaySize().x, deviceInfo.getDisplaySize().y)
                ),
                claims,
                parameters,
                getReferrer()
        );
    }

    @NonNull
    ApplicationVersion getAppVersion() {
        return versionBundle.getApplicationVersion();
    }

    @NonNull
    Version getSdkVersion() {
        return versionBundle.getSdkVersion();
    }

    @NonNull
    String getDeviceId() {
        return deviceId;
    }

    @Nullable
    String getAdvertiserId() {
        return advertiserId;
    }

    boolean getHasLimitedAdTracking() {
        return hasLimitedAdTracking;
    }

    @Nullable
    String getTrackingId() {
        return trackingId;
    }

    @NonNull
    String getTrackingProvider() {
        return trackingProvider;
    }

    @Nullable
    String getCountryIso() {
        return deviceInfo.getCountryIso();
    }

    @NonNull
    String getDeviceName() {
        return deviceInfo.getDeviceName();
    }

    @NonNull
    String getDeviceModel() {
        return deviceInfo.getDeviceModel();
    }

    @NonNull
    String getDeviceProduct() {
        return deviceInfo.getDeviceProduct();
    }

    @NonNull
    DeviceType getDeviceType() {
        return deviceInfo.getDeviceType();
    }

    @NonNull
    String getOsVersion() {
        return deviceInfo.getOsVersion();
    }

    @NonNull
    String getOsName() {
        return deviceInfo.getOsName();
    }

    int getDisplayWidth() {
        return deviceInfo.getDisplaySize().x;
    }

    int getDisplayHeight() {
        return deviceInfo.getDisplaySize().y;
    }

    @Nullable
    ReferrerDetails getReferrerDetails() {
        return referrerDetails;
    }

    @NonNull
    Date getClientDate() {
        return clientDate;
    }

    @Nullable
    private DTOAttributionInputReferrer getReferrer() {
        if (referrerDetails == null) {
            return null;
        }

        String referrerHash = referrerDetails.getInstallReferrer();
        if (referrerHash.isEmpty()) {
            return null;
        }

        return new DTOAttributionInputReferrer(
                referrerHash,
                new Date(referrerDetails.getReferrerClickTimestampSeconds() * 1000),
                new Date(referrerDetails.getInstallBeginTimestampSeconds() * 1000),
                clientDate,
                new Date(referrerDetails.getReferrerClickTimestampServerSeconds() * 1000),
                new Date(referrerDetails.getInstallBeginTimestampServerSeconds() * 1000),
                referrerDetails.getInstallVersion()
        );
    }
}
