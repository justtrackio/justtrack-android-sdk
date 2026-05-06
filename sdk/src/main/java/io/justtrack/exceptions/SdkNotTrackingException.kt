package io.justtrack.exceptions

/**
 * Thrown when an SDK operation is attempted before the SDK has been started.
 */
class SdkNotTrackingException : RuntimeException("The SDK is not running. Call the start() method first and then retry your request.")
