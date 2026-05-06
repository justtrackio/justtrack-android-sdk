package io.justtrack;

import androidx.annotation.NonNull;

class NetworkProblemException extends Exception {
    NetworkProblemException(@NonNull Throwable cause) {
        super("HTTP request failed", cause);
    }
}
