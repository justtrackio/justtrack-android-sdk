package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

class DTOAttributionOutputUser {
    @NonNull
    private final String installId;
    @NonNull
    private final String type;
    @Nullable
    private final Integer testGroup;
    private final boolean redownload;

    DTOAttributionOutputUser(@NonNull DTOAttributionOutputUser obj) {
        this.installId = obj.installId;
        this.type = obj.type;
        this.testGroup = obj.testGroup;
        this.redownload = obj.redownload;
    }

    DTOAttributionOutputUser(
            @NonNull String installId,
            @NonNull String type,
            @Nullable Integer testGroup,
            boolean redownload) {
        this.installId = installId;
        this.type = type;
        this.testGroup = testGroup;
        this.redownload = redownload;
    }

    DTOAttributionOutputUser(@NonNull JSONObject obj) throws JSONException {
        this.installId = obj.getString("installId");
        this.type = obj.getString("type");

        if (obj.has("testGroup") && obj.get("testGroup") != JSONObject.NULL) {
            this.testGroup = obj.getInt("testGroup");
        } else {
            this.testGroup = null;
        }
        this.redownload = obj.getBoolean("redownload");
    }

    @NonNull
    String getInstallId() {
        return this.installId;
    }

    @NonNull
    String getType() {
        return this.type;
    }

    @Nullable
    Integer getTestGroup() {
        return this.testGroup;
    }

    boolean isRedownload() {
        return this.redownload;
    }
}