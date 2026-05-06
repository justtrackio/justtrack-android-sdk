package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.util.Random;

class AttributionErrorClassifier implements ErrorClassifier {
    private static final @NonNull ErrorClassifier INSTANCE = new AttributionErrorClassifier();

    static ErrorClassifier getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean unrecoverable(@NonNull Throwable exception) {
        if (exception instanceof BadResponseException) {
            int code = ((BadResponseException) exception).getResponseCode();

            if (code >= 400 && code < 500) {
                // something from our request was wrong, no point in retrying, the backend needs to be fixed
                return true;
            }

            if (code >= 500) {
                // the backend is having problems right now, so we will slow down hard, but retry eventually
                return false;
            }

            // unknown status, just retry...
            return false;
        }

        if (exception instanceof NetworkProblemException) {
            // network is down? This is the prime reason why we retry
            return false;
        }

        if (exception instanceof JSONException) {
            // we will get the same response again anyway, someone needs to fix the server for this to work
            return true;
        }

        @Nullable Throwable cause = exception.getCause();
        if (cause != null) {
            return unrecoverable(cause);
        }

        return false;
    }

    @Override
    public double waitTime(@NonNull Throwable exception) {
        if (exception instanceof BadResponseException) {
            int code = ((BadResponseException) exception).getResponseCode();

            if (code >= 500) {
                // the backend is having problems right now, so we wait up to 5 minutes before retrying.
                // while this is harsh, there is no point in bombarding the backend, most users will
                // have quit the app by then.
                double minutes = new Random().nextDouble() * 5;

                return minutes * 60 * 1000;
            }

            return 0;
        }

        @Nullable Throwable cause = exception.getCause();
        if (cause != null) {
            return waitTime(cause);
        }

        return 0;
    }
}
