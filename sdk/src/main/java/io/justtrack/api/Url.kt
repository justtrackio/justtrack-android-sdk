package io.justtrack.api

import android.annotation.SuppressLint
import android.net.Uri
import java.net.URL

@SuppressLint("UseKtx")
internal fun stripScheme(url: String): String {
    // this checks if it's a proper URL
    URL(url)

    val uri = Uri.parse(url)

    return buildString {
        uri.authority?.let { append(it) }
        uri.encodedPath?.let { append(it) }
        uri.encodedQuery?.let { append("?$it") }
        uri.encodedFragment?.let { append("#$it") }
    }
}
