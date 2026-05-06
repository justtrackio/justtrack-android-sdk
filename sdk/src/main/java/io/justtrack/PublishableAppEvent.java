package io.justtrack;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Date;
import java.util.Map;

import io.justtrack.events.Unit;
import io.justtrack.versions.SdkVersion;

class PublishableAppEvent {
    @NonNull
    private final String name;
    @NonNull
    private final Map<String, String> dimensions;
    private final double value;
    @Nullable
    private final Unit unit;
    @Nullable
    private final String currency;
    @NonNull
    private final String sessionId;
    @NonNull
    private final SdkVersion sdkVersion;
    @NonNull
    private final Date happenedAt;

    PublishableAppEvent(@NonNull String name,
                        @NonNull Map<String, String> dimensions,
                        double value,
                        @Nullable Unit unit,
                        @Nullable String currency,
                        @NonNull String sessionId,
                        @NonNull SdkVersion sdkVersion,
                        @Nullable Date happenedAt) {
        this.name = name;
        this.dimensions = dimensions;
        this.value = value;
        this.unit = unit;
        this.currency = currency;
        this.sessionId = sessionId;
        this.sdkVersion = sdkVersion;
        this.happenedAt = happenedAt != null ? happenedAt : new Date();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof PublishableAppEvent)) {
            return false;
        }
        PublishableAppEvent other = (PublishableAppEvent) obj;
        //noinspection EqualsReplaceableByObjectsCall
        return name.equals(other.name)
                && dimensions.equals(other.dimensions)
                && value == other.value
                && unit == other.unit
                && (currency == null ? other.currency == null : currency.equals(other.currency))
                && sessionId.equals(other.sessionId)
                && sdkVersion == other.sdkVersion
                && happenedAt.equals(other.happenedAt);
    }

    @Override
    public int hashCode() {
        final int valueHash;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            valueHash = Double.hashCode(value);
        } else {
            valueHash = (int) value;
        }

        int hashCode = name.hashCode();
        hashCode = 31 * hashCode + dimensions.hashCode();
        hashCode = 31 * hashCode + valueHash;
        hashCode = 31 * hashCode + (unit == null ? 0 : unit.hashCode());
        hashCode = 31 * hashCode + (currency == null ? 0 : currency.hashCode());
        hashCode = 31 * hashCode + sessionId.hashCode();
        hashCode = 31 * hashCode + sdkVersion.hashCode();
        hashCode = 31 * hashCode + happenedAt.hashCode();

        return hashCode;
    }

    @NonNull
    @Override
    public String toString() {
        final Formatter formatter = Formatter.INSTANCE;
        StringBuilder buffer = new StringBuilder();
        buffer.append("[PublishableUserEvent ").append(name);
        if (!dimensions.isEmpty()) {
            buffer.append(", dimensions = [");
            boolean firstDimension = true;
            for (Map.Entry<String, String> entry : dimensions.entrySet()) {
                if (firstDimension) {
                    firstDimension = false;
                } else {
                    buffer.append(", ");
                }
                String dimension = entry.getKey();
                String value = entry.getValue();
                if (!TextUtils.isNullOrEmpty(value)) {
                    buffer.append(dimension).append(" = ").append(value);
                }
            }
            buffer.append("]");
        }

        buffer.append(", value = ").append(value).append(" ");
        if (unit != null) {
            buffer.append(unit);
        } else if (currency != null) {
            buffer.append(currency);
        } else {
            buffer.append("null");
        }
        buffer.append(", sessionId = ").append(sessionId);
        buffer.append(", sdkVersionMajor = ").append(sdkVersion.getMajor());
        buffer.append(", sdkVersionMinor = ").append(sdkVersion.getMinor());
        buffer.append(", sdkVersionPatch = ").append(sdkVersion.getPatch());
        buffer.append(", sdkVersionName = ").append(sdkVersion.getName());
        buffer.append(", happenedAt = ").append(formatter.formatDateMilliseconds(happenedAt));
        buffer.append("]");
        return buffer.toString();
    }

    @NonNull
    String getName() {
        return name;
    }

    @NonNull
    Map<String, String> getDimensions() {
        return dimensions;
    }

    double getValue() {
        return value;
    }

    @Nullable
    Unit getUnit() {
        return unit;
    }

    @Nullable
    String getCurrency() {
        return currency;
    }

    @NonNull
    @Hidden
    public Date getHappenedAt() {
        return happenedAt;
    }

    @NonNull
    String getSessionId() {
        return this.sessionId;
    }

    @NonNull
    SdkVersion getSdkVersion() {
        return sdkVersion;
    }

    @VisibleForTesting
    public boolean equalWithoutDate(@Nullable Object obj) {
        if (!(obj instanceof PublishableAppEvent)) {
            return false;
        }
        PublishableAppEvent other = (PublishableAppEvent) obj;
        //noinspection EqualsReplaceableByObjectsCall
        return name.equals(other.name)
                && dimensions.equals(other.dimensions)
                && value == other.value
                && unit == other.unit
                && (currency == null ? other.currency == null : currency.equals(other.currency))
                && sessionId.equals(other.sessionId)
                && sdkVersion.getMajor() == other.sdkVersion.getMajor()
                && sdkVersion.getMinor() == other.sdkVersion.getMinor()
                && sdkVersion.getPatch() == other.sdkVersion.getPatch()
                && sdkVersion.getName().equals(other.sdkVersion.getName()
        );
    }
}