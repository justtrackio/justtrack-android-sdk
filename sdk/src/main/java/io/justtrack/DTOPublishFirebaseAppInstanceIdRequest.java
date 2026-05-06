package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

class DTOPublishFirebaseAppInstanceIdRequest implements JSONEncodable {
    @NonNull
    private final String uuid;
    @NonNull
    private final String firebaseInstanceId;

    DTOPublishFirebaseAppInstanceIdRequest(@NonNull DTOPublishFirebaseAppInstanceIdRequest obj) {
        this.uuid = obj.uuid;
        this.firebaseInstanceId = obj.firebaseInstanceId;
    }

    DTOPublishFirebaseAppInstanceIdRequest(
            @NonNull String uuid,
            @NonNull String firebaseInstanceId) {
        this.uuid = uuid;
        this.firebaseInstanceId = firebaseInstanceId;
    }

    DTOPublishFirebaseAppInstanceIdRequest(@NonNull JSONObject obj) throws JSONException {
        this.uuid = obj.getString("uuid");
        this.firebaseInstanceId = obj.getString("firebaseInstanceId");
    }

    @NonNull
    @Override
    public JSONObject toJSON(@NonNull Formatter formatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("uuid", this.uuid);
        obj.put("firebaseInstanceId", this.firebaseInstanceId);
        return obj;
    }

    @NonNull
    String getFirebaseInstanceId() {
        return this.firebaseInstanceId;
    }
}