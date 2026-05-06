package io.justtrack.ads

/**
 * The AdFormat describes where the ad is located in your app.
 */
enum class AdUnit(
    /** The encoded name of this ad unit. */
    val encodedName: String,
) {
    /**
     * A basic ad format that appears at the top and bottom of the device screen.
     */
    Banner("banner"),

    /**
     * Full-page ads appear at natural breaks and transitions, such as level completion.
     */
    Interstitial("interstitial"),

    /**
     * Ads reward users for watching short videos and interacting with playable ads and surveys.
     */
    Rewarded("rewarded"),

    /**
     * Full-page ad format that rewards users for viewing ads during natural breaks or transitions.
     */
    RewardedInterstitial("rewarded_interstitial"),

    /**
     * Customizable ad format that matches the look and feel of your app. Ads appear inline with app content.
     */
    Native("native"),

    /**
     * Ad format that appears when users open or switch back to your app. Ad overlays loading screen.
     */
    AppOpen("app_open"),

    /**
     * A special kind of banner also known as leaderboard which is displayed typically at the top of the page.
     */
    Leader("leader"),

    /**
     * A larger version of banner ads, with the same functionality as interstitial ads.
     */
    MediumRectangle("mrec"),
}
