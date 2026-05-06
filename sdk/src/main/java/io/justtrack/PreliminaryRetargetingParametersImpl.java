package io.justtrack;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.justtrack.retargeting.PreliminaryRetargetingParameters;

class PreliminaryRetargetingParametersImpl extends RetargetingParametersImpl
        implements PreliminaryRetargetingParameters, Callback<PreliminaryRetargetingParameters.ValidateResult> {
    private final @NonNull ResolvableFuture<ValidateResult> validatedResult;

    private PreliminaryRetargetingParametersImpl(@NonNull String url, @NonNull Map<String, String> parameters) {
        super(true, url, parameters);
        validatedResult = new ResolvableFuture<>();
    }

    @NonNull
    @Override
    public AsyncFuture<ValidateResult> validate() {
        return validatedResult;
    }

    @Override
    public void resolve(@NonNull ValidateResult response) {
        validatedResult.resolve(response);
    }

    @Override
    public void reject(@NonNull Throwable exception) {
        validatedResult.reject(exception);
    }

    @Nullable
    static PreliminaryRetargetingParametersImpl fromIntent(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }

        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            return null;
        }

        @Nullable String url = intent.getDataString();
        @Nullable Bundle extras = intent.getExtras();

        if (url == null || extras == null) {
            return null;
        }

        Map<String, String> parameters = new HashMap<>();

        for (String key : extras.keySet()) {
            String value = extras.getString(key);
            if (value != null) {
                parameters.put(key, value);
            }
        }

        return new PreliminaryRetargetingParametersImpl(url, parameters);
    }
}
