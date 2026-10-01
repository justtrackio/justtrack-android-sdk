package io.justtrack

internal object TextUtils {
    @JvmStatic
    fun isNullOrEmpty(s: CharSequence?): Boolean {
        return s == null || s.isEmpty()
    }

    @JvmStatic
    @Suppress("MagicNumber") // encoding checks require magic numbers
    fun isASCII(value: String): Boolean {
        for (i in 0..<value.length) {
            val chr = value[i]

            if (chr.code !in 0x20..0x7E) {
                return false
            }
        }

        return true
    }

    @JvmStatic
    @Suppress("MagicNumber") // encoding checks require magic numbers
    fun isISO88591(value: String): Boolean {
        for (i in 0..<value.length) {
            val chr = value[i]

            if (chr.code !in 0x20..0xFF || (chr.code in 0x7f..<0xA0)) {
                return false
            }
        }

        return true
    }
}
