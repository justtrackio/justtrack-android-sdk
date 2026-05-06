package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

class DTOIntegrityToken implements JSONEncodable {
    @Nullable
    private final String integrityToken;
    @NonNull
    private final String installInstanceId;
    @Nullable
    private final Integer errorCode;
    @Nullable
    private final String errorMessage;

    DTOIntegrityToken(@NonNull DTOIntegrityToken obj) {
        this.integrityToken = obj.integrityToken;
        this.installInstanceId = obj.installInstanceId;
        this.errorCode = obj.errorCode;
        this.errorMessage = obj.errorMessage;
    }

    DTOIntegrityToken(
            @Nullable String integrityToken,
            @NonNull String installInstanceId,
            @Nullable Integer errorCode,
            @Nullable String errorMessage) {
        this.integrityToken = integrityToken;
        this.installInstanceId = installInstanceId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    DTOIntegrityToken(@NonNull JSONObject obj) throws JSONException {

        if (obj.has("integrityToken") && obj.get("integrityToken") != JSONObject.NULL) {
            this.integrityToken = obj.getString("integrityToken");
        } else {
            this.integrityToken = null;
        }
        this.installInstanceId = obj.getString("installInstanceId");

        if (obj.has("errorCode") && obj.get("errorCode") != JSONObject.NULL) {
            this.errorCode = obj.getInt("errorCode");
        } else {
            this.errorCode = null;
        }

        if (obj.has("errorMessage") && obj.get("errorMessage") != JSONObject.NULL) {
            this.errorMessage = obj.getString("errorMessage");
        } else {
            this.errorMessage = null;
        }
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();

        if (integrityToken != null) {
            obj.put("integrityToken", this.integrityToken);
        } else {
            obj.put("integrityToken", JSONObject.NULL);
        }
        obj.put("installInstanceId", this.installInstanceId);

        if (errorCode != null) {
            obj.put("errorCode", this.errorCode);
        } else {
            obj.put("errorCode", JSONObject.NULL);
        }

        if (errorMessage != null) {
            obj.put("errorMessage", this.errorMessage);
        } else {
            obj.put("errorMessage", JSONObject.NULL);
        }
        return obj;
    }

    @Nullable
    String getIntegrityToken() {
        return this.integrityToken;
    }

    @NonNull
    String getInstallInstanceId() {
        return this.installInstanceId;
    }

    @Nullable
    Integer getErrorCode() {
        return this.errorCode;
    }

    @Nullable
    String getErrorMessage() {
        return this.errorMessage;
    }
}