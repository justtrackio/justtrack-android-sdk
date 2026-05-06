package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class BadResponseException extends Exception {
    private final int code;
    private final @Nullable String body;

    BadResponseException(@NonNull String message, int code) {
        this(message, code, null);
    }

    BadResponseException(@NonNull String message, int code, @Nullable String body) {
        super(message);
        this.code = code;
        this.body = body;
    }

    BadResponseException(@NonNull String message, int code, @Nullable String body, @NonNull Throwable cause) {
        super(message, cause);
        this.code = code;
        this.body = body;
    }

    int getResponseCode() {
        return code;
    }

    @Nullable
    String getBody() {
        return body;
    }

    @NonNull
    static String formatBadResponseStatusMessage(
            int code,
            @Nullable String message,
            @Nullable String body) {
        if (code == 401) {
            String msg1 = "Request could not be authenticated. Is the API token correct?";
            String msg2 = "See https://docs.justtrack.io/sdk/latest/overview/find-your-justtrack-token how to get an API token.";
            String msg3 = "";
            String msg4 = "Response Body: ";
            if (body != null) {
                if (body.length() > 64) {
                    msg4 = msg4 + body.substring(0, 64) + "...";
                } else {
                    msg4 = msg4 + body;
                }
            }

            return formatErrorBox(msg1, msg2, msg3, msg4);
        }

        return "Received invalid response status " + code + " " + message + (body == null ? "" : " with body " + body);
    }

    static String formatErrorBox(String... messages) {
        StringBuilder sb = new StringBuilder();
        int lineLength = 0;
        for (String msg : messages) {
            lineLength = Math.max(lineLength, msg.length());
        }
        lineLength += 4;
        sb.append('\n');
        for (int i = 0; i < lineLength; i++) {
            sb.append('*');
        }
        String msg6 = sb.toString();
        for (String msg : messages) {
            sb.append("\n* ").append(msg);
            for (int i = msg.length(); i < lineLength - 4; i++) {
                sb.append(' ');
            }
            sb.append(" *");
        }
        sb.append(msg6).append('\n');

        return sb.toString();
    }
}
