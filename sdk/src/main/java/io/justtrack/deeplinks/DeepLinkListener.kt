package io.justtrack.deeplinks

/**
 * Listener that is notified when the user clicks a deep link tracked by the justtrack SDK.
 */
interface DeepLinkListener {
    /**
     * Called when a tracked deep link is clicked.
     *
     * @param deepLink The deep link data associated with the click.
     * @return How the deep link was handled, or null if the listener cannot determine the outcome.
     */
    fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled?
}
