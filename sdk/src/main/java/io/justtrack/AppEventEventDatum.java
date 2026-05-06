package io.justtrack;

import androidx.annotation.NonNull;

import org.json.JSONObject;

class AppEventEventDatum extends DTOAppEventEvent implements NamedDatum {
    AppEventEventDatum(@NonNull DTOAppEventEvent obj) {
        super(obj);
    }

    @NonNull
    @Override
    public String getDatum() {
        return getName();
    }

    @NonNull
    @Override
    public JSONObject getDimensions() {
        return super.getDimensions();
    }
}
