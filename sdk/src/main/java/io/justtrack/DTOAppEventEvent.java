package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

import io.justtrack.events.Unit;

class DTOAppEventEvent implements JSONEncodable {
    @NonNull
    private final UUID id;
    @NonNull
    private final String name;
    @Nullable
    private final JSONObject dimensions;
    private final double value;
    @Nullable
    private final Unit unit;
    @Nullable
    private final String currency;
    @NonNull
    private final String sessionId;
    @NonNull
    private final Date happenedAt;
    @NonNull
    private final Long sequenceNumber;

    DTOAppEventEvent(@NonNull DTOAppEventEvent obj) {
        this.id = obj.id;
        this.name = obj.name;
        this.dimensions = obj.dimensions;
        this.value = obj.value;
        this.unit = obj.unit;
        this.currency = obj.currency;
        this.sessionId = obj.sessionId;
        this.happenedAt = obj.happenedAt;
        this.sequenceNumber = obj.sequenceNumber;
    }

    DTOAppEventEvent(
            @NonNull UUID id,
            @NonNull String name,
            @Nullable JSONObject dimensions,
            double value,
            @Nullable Unit unit,
            @Nullable String currency,
            @NonNull String sessionId,
            @NonNull Date happenedAt,
            @NonNull Long sequenceNumber) {
        this.id = id;
        this.name = name;
        this.dimensions = dimensions;
        this.value = value;
        this.unit = unit;
        this.currency = currency;
        this.sessionId = sessionId;
        this.happenedAt = happenedAt;
        this.sequenceNumber = sequenceNumber;
    }

    DTOAppEventEvent(@NonNull JSONObject obj, @NonNull Formatter formatter) throws JSONException, ParseException {
        this.id = UUID.fromString(obj.getString("id"));
        this.name = obj.getString("name");

        if (obj.has("dimensions") && obj.get("dimensions") != JSONObject.NULL) {
            this.dimensions = obj.getJSONObject("dimensions");
        } else {
            this.dimensions = null;
        }
        this.value = obj.getDouble("value");

        if (obj.has("unit") && obj.get("unit") != JSONObject.NULL) {
            this.unit = Unit.valueOf(obj.getString("unit").toUpperCase());
        } else {
            this.unit = null;
        }

        if (obj.has("currency") && obj.get("currency") != JSONObject.NULL) {
            this.currency = obj.getString("currency");
        } else {
            this.currency = null;
        }
        this.sessionId = obj.getString("sessionId");
        this.happenedAt = formatter.parseDate(obj.getString("happenedAt"));
        this.sequenceNumber = obj.getLong("sequenceNumber");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", this.id.toString());
        obj.put("name", this.name);

        if (dimensions != null && dimensions.length() > 0) {
            obj.put("dimensions", this.dimensions);
        }

        if (unit != null || currency != null) {
            obj.put("value", this.value);
        }

        if (unit != null) {
            obj.put("unit", this.unit.toString());
        }

        if (currency != null) {
            obj.put("currency", this.currency);
        }

        obj.put("sessionId", this.sessionId);
        obj.put("happenedAt", formatter.formatDateMilliseconds(this.happenedAt));
        obj.put("sequenceNumber", this.sequenceNumber);
        return obj;
    }

    @NonNull
    UUID getId() {
        return this.id;
    }

    @NonNull
    String getName() {
        return this.name;
    }

    @Nullable
    JSONObject getDimensions() {
        return this.dimensions;
    }

    double getValue() {
        return this.value;
    }

    @Nullable
    Unit getUnit() {
        return this.unit;
    }

    @Nullable
    String getCurrency() {
        return this.currency;
    }

    @NonNull
    String getSessionId() {
        return this.sessionId;
    }

    @NonNull
    Date getHappenedAt() {
        return this.happenedAt;
    }

    @NonNull
    Long getSequenceNumber() {
        return this.sequenceNumber;
    }
}
