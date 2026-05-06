package io.justtrack;

import android.content.Context;

import androidx.annotation.NonNull;

import io.justtrack.installreferrer.api.ReferrerDetails;

interface InstallReferrerProvider {
    @NonNull
    Task<ReferrerDetails> newTask(@NonNull Context context);
}
