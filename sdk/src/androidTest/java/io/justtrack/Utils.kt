package io.justtrack

fun getBadResponseBody(throwable: Throwable?): String? {
    if (throwable is BadResponseException) {
        return throwable.body
    }

    val cause = throwable?.cause

    return if (cause != null) getBadResponseBody(cause) else null
}
