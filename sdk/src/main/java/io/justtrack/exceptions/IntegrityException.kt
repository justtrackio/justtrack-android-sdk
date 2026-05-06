package io.justtrack.exceptions

internal class IntegrityException(
    val errorCode: Int?,
    val isRetryAbleErrorCode: Boolean,
    val errorMessage: String?,
    val throwable: Throwable?,
) : Exception(errorMessage, throwable) {

    override fun toString(): String {
        return "IntegrityError(errorCode=$errorCode, isRetryAble=$isRetryAbleErrorCode, errorMessage=$errorMessage, throwable=$throwable)"
    }
}
