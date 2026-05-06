package io.justtrack.deeplinks

/**
 * Describes how a [DeepLinkListener] handled (or chose not to handle) a deep link.
 */
enum class DeepLinkHandled {
    /** The deep link was recognized and fully handled by the listener. */
    DEEP_LINK_HANDLED,

    /** The deep link was recognized but intentionally ignored by the listener. */
    DEEP_LINK_IGNORED,

    /** The deep link was not recognized and therefore not handled. */
    DEEP_LINK_NOT_HANDLED,
}
