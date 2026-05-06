package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOPublishCustomUserIdRequest implements JSONEncodable {
    @NonNull
    private final String installId;
    @NonNull
    private final String customUserId;

    DTOPublishCustomUserIdRequest(@NonNull DTOPublishCustomUserIdRequest obj) {
        this.installId = obj.installId;
        this.customUserId = obj.customUserId;
    }

    DTOPublishCustomUserIdRequest(
            @NonNull String installId,
            @NonNull String customUserId) {
        this.installId = installId;
        this.customUserId = customUserId;
    }

    DTOPublishCustomUserIdRequest(@NonNull JSONObject obj) throws JSONException {
        this.installId = obj.getString("installId");
        this.customUserId = obj.getString("customUserId");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("installId", this.installId);
        obj.put("customUserId", this.customUserId);
        return obj;
    }

    @NonNull
    String getInstallId() {
        return this.installId;
    }

    @NonNull
    String getCustomUserId() {
        return this.customUserId;
    }
}