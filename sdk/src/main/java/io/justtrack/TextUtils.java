package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class TextUtils {
    static boolean isNullOrEmpty(@Nullable CharSequence s) {
        return s == null || s.length() == 0;
    }

    static boolean isASCII(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            char chr = value.charAt(i);

            if (chr < 0x20 || chr > 0x7E) {
                return false;
            }
        }

        return true;
    }

    static boolean isISO8859_1(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            char chr = value.charAt(i);

            if (chr < 0x20 || chr > 0xFF || (chr > 0x7E && chr < 0xA0)) {
                return false;
            }
        }

        return true;
    }
}
