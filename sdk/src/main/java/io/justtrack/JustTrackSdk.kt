package io.justtrack

import android.content.Intent
import io.justtrack.ads.AdImpression
import io.justtrack.attribution.AdvertiserIdInfo
import io.justtrack.attribution.Attribution
import io.justtrack.config.RemoteConfig
import io.justtrack.deeplinks.DeepLinkListener
import io.justtrack.events.Money
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.retargeting.PreliminaryRetargetingParameters
import io.justtrack.retargeting.PreliminaryRetargetingParametersListener
import io.justtrack.retargeting.RetargetingParameters
import io.justtrack.retargeting.RetargetingParametersListener
import java.util.Date

/**
 * An instance of this class represents your handle to the backend services to attribute a user
 * and track events about the user. You should create exactly one instance during app startup
 * and hold onto it the whole time your application runs.
 */
interface JustTrackSdk {
    /**
     * Get the unique id of the current install of that user.
     *
     * @return The install instance id for the current user.
     */
    val installInstanceId: AsyncFuture<String>

    /**
     * Remote config functionality for retrieving and activating experiment assignments
     *
     * @return The remote config instance
     */
    val remoteConfig: RemoteConfig

    /**
     * Use this method to share the information about the test group assigned to the user.
     *
     * @param experiment The name of the A/B test. Must be shorter than 255 characters
     * and consist only of printable ASCII characters (U+0020 to U+007E).
     *
     * @param variant The test group the user is assigned to. Must be shorter than 255 characters
     * and consist only of printable ASCII characters (U+0020 to U+007E).
     *
     * @param happenedAt The time at which the user was assigned to the test group.
     * Defaults to the time at which the server received the assignment request if not provided.
     *
     * @return A future which resolves as soon as the backend was notified about the experiment variant.
     */
    fun setExperimentVariant(experiment: String?, variant: String?, happenedAt: Date?): AsyncFuture<Void>

    /**
     * Use this method to share the information about the test group assigned to the user.
     *
     * @param experiment The name of the A/B test. Must be shorter than 255 characters
     * and consist only of printable ASCII characters (U+0020 to U+007E).
     *
     * @param variant The test group the user is assigned to. Must be shorter than 255 characters
     * and consist only of printable ASCII characters (U+0020 to U+007E).
     *
     * @param tags You can add tags to your experiments which can describe the purpose. You can add a maximum of 5 tags.
     * Each tag must be shorter than 64 characters and consist only of printable ASCII characters (U+0020 to U+007E).
     *
     * @param happenedAt The time at which the user was assigned to the test group.
     * Defaults to the time at which the server received the assignment request if not provided.
     *
     * @return A future which resolves as soon as the backend was notified about the experiment variant.
     */
    fun setExperimentVariant(experiment: String?, variant: String?, tags: List<String?>?, happenedAt: Date?): AsyncFuture<Void>

    /**
     * Retrieve the test group of the user. The test group is retrieved from the justtrack backend
     * and resolves to null should the request fail. It doesn't change once some value (even null)
     * has been returned for it.
     *
     *
     * In some cases, the backend can't compute a test group id. In that case, the returned value
     * is also null.
     *
     * @return The test group of the user or null.
     */
    @Deprecated("Will be removed in future releases")
    fun getTestGroupId(): AsyncFuture<Int>

    /**
     * Send attribution information about the current user of your app to the backend and provide
     * data about the origin of the user to your callback. You should call this method on startup
     * to get your justtrack UUID for the user (internal identifier used by justtrack to uniquely identify
     * a user of an app).
     *
     *
     * If you call this method on the second app launch again (and the user did not delete application
     * data), the last response from the backend will be provided to you again. Thus, calling this method
     * is idempotent.
     *
     *
     * If no attribution can be obtained (because the network is down), the call is automatically retried
     * as soon as network connectivity is restored. This will not carry over to the future returned from
     * the first call to this method (but subsequent calls will return a future for the current try).
     * You should use [.registerAttributionListener] to get notified as soon
     * as a valid attribution was obtained.
     *
     *
     * If the future fails, it throws an [AttributionException] (wrapped in an [java.util.concurrent.ExecutionException]).
     *
     * @return A future which resolves to the attribution of your user or throws an exception in case of an error.
     */
    val attribution: AsyncFuture<Attribution>

    /**
     * Retrieve the retargeting parameters your app was started with. These parameters are only available
     * on the first start of the app after a retargeting click was performed.
     *
     * @return A future which resolves to the retargeting parameters (if any) or null.
     */
    val retargetingParameters: AsyncFuture<RetargetingParameters>

    /**
     * Retrieve the preliminary retargeting parameters extracted from the intent your app was started with.
     *
     * @return The preliminary retargeting parameters (if any) or null.
     */
    val preliminaryRetargetingParameters: PreliminaryRetargetingParameters?

    /**
     * Retrieve the version of your app during install (during the first initialization of the SDK to be precise).
     *
     * @return A future which resolves to the version of your app during app install.
     */
    val appVersionAtInstall: AsyncFuture<ApplicationVersion>

    /**
     * Retrieve the current version of the SDK.
     *
     * @return The current version of the SDK.
     */
    val sdkVersion: Version

    /**
     * Retrieve information about the advertiser id of the user. The returned future always resolves
     * to a non-null [AdvertiserIdInfo], but [AdvertiserIdInfo.getAdvertiserId] might
     * return null if we failed to retrieve the advertiser id of the user for some reason.
     *
     * @return Information about the advertiser id of the user.
     */
    val advertiserIdInfo: AsyncFuture<AdvertiserIdInfo>

    /**
     * Register a new attribution listener instance.
     *
     * @param attributionListener An attribution listener.
     * @return A subscription object to unregister the listener again.
     */
    fun registerAttributionListener(attributionListener: AttributionListener): Subscription

    /**
     * Register a new retargeting parameters listener instance.
     *
     * @param retargetingParametersListener A retargeting parameters listener.
     * @return A subscription object to unregister the listener again.
     */
    fun registerRetargetingParametersListener(retargetingParametersListener: RetargetingParametersListener): Subscription

    /**
     * Register a new preliminary retargeting parameters listener instance.
     *
     * @param preliminaryRetargetingParametersListener A preliminary retargeting parameters listener.
     * @return A subscription object to unregister the listener again.
     */
    fun registerPreliminaryRetargetingParametersListener(
        preliminaryRetargetingParametersListener: PreliminaryRetargetingParametersListener,
    ): Subscription

    /**
     * Register a new deeplink listener instance.
     *
     * @param deepLinkListener A deeplink listener.
     * @return A subscription object to unregister the listener again.
     */
    fun registerDeepLinkListener(deepLinkListener: DeepLinkListener): Subscription

    /**
     * Notify the SDK about your app being destroyed. Will unregister all observers setup during
     * SDK initialization. Afterwards you need to initialize the SDK again. Thus, you always have
     * to create a new instance of the SDK in your onCreate callback.
     */
    fun shutdown()

    /**
     * Notify the SDK about a new [Intent] arriving at your [android.app.Activity].
     * This switches the SDK to await new Intents to always arrive via this method. If you implement
     * [android.app.Activity.onNewIntent] and call [android.app.Activity.setIntent]
     * from there, you don't need to call this method.
     *
     * @param newIntent The new Intent which brought your app to the foreground.
     */
    fun onNewIntent(newIntent: Intent?)

    /**
     * Forward a user id to the justtrack backend.
     *
     *
     * It is safe to call this method multiple times with the same user id. If the user id of
     * the current user changes for some reason, you have to call this method as soon as possible
     * (otherwise we will not be able to recognize the new custom user id until this method is called).
     * You can call this method multiple times while the app is running.
     *
     *
     * The user id must be shorter than 4096 characters and consist only of printable ASCII
     * characters (U+0020 to U+007E).
     *
     * @param userId The user id to forward.
     * @return A future which resolves as soon as the backend was notified about the user id.
     */
    fun setUserId(userId: String): AsyncFuture<Boolean>

    /**
     * Forward the Firebase app instance id (see [FirebaseAnalytics.getAppInstanceId()](https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics#public-taskstring-getappinstanceid)
     * to how to obtain one) to the justtrack backend.
     *
     * This method is only required if you are manually configuring the SDK. If you are using
     * {@link JustTrackSdk#integrateWith} (see {@link JustTrackSdk#integrateWith}), then you do not need to call this method yourself.
     *
     * The Firebase app instance id must be between 8 and 256 characters and consist only of printable ASCII
     * characters (U+0020 to U+007E).
     *
     * @param firebaseAppInstanceId The id to forward.
     * @return A future which resolves as soon as the backend was notified about the app instance id.
     */
    fun setFirebaseAppInstanceId(firebaseAppInstanceId: String): AsyncFuture<Boolean>

    /**
     * The justtrack SDK can automatically track in-app product and subscription purchases and
     * forward them to the justtrack backend. It is enabled by default, but this method allows you
     * to configure the automation for your needs.
     *
     * @param enabled Set this to true to automatically forward in-app product and subscription purchases.
     */
    fun setAutomaticInAppPurchaseTracking(enabled: Boolean)

    /**
     * Publish an event the user caused to the backend. Events are send in batches to the backend and
     * persisted to disk until they have successfully been sent.
     *
     * @param event The event you want to publish.
     * @return A future which resolves to an implementation defined value if your event was published
     * or throws an exception if the event could not be published. If you don't care whether the event
     * has already reached the backend you can just ignore the returned future.
     */
    @Deprecated(
        message = "Deprecated in favor of .track()",
        replaceWith = ReplaceWith("track(event)"),
    )
    fun publishEvent(event: AppEvent): AsyncFuture<Void>

    /**
     * Track an event the user caused to the backend. Events are send in batches to the backend and
     * persisted to disk until they have successfully been sent.
     *
     * @param event The event you want to publish.
     * @return A future which resolves to an implementation defined value if your event was published
     * or throws an exception if the event could not be published. If you don't care whether the event
     * has already reached the backend you can just ignore the returned future.
     */
    fun track(event: AppEvent): AsyncFuture<Void>

    /**
     * Track an event the user caused to the backend. Events are send in batches to the backend and
     * persisted to disk until they have successfully been sent.
     *
     * @param eventName The name of the event you want to publish.
     * @return A future which resolves to an implementation defined value if your event was published
     * or throws an exception if the event could not be published. If you don't care whether the event
     * has already reached the backend you can just ignore the returned future.
     */
    fun track(eventName: String): AsyncFuture<Void>

    /**
     * Track an event the user caused to the backend. Events are sent in batches to the backend and
     * persisted to disk until they have successfully been sent.
     *
     * @param eventName The name of the event you want to publish.
     * @param dimensions The dimensions of the event you want to publish.
     * @return A future which resolves to an implementation defined value if your event was published
     * or throws an exception if the event could not be published. If you don't care whether the event
     * has already reached the backend, you can just ignore the returned future.
     */
    fun track(eventName: String, dimensions: Map<String, String>): AsyncFuture<Void>

    /**
     * Forward an ad impression to the justtrack backend. Depending on the ad SDK we will use this
     * data to display the correct amount of ad revenue your app generated.
     *
     * @param adImpression adImpression The ad impression data to be forwarded to the justtrack backend.
     *
     * @return A future which resolves to an implementation defined value
     * if the ad impression was forwarded to the justtrack backend.
     */
    fun forwardAdImpression(adImpression: AdImpression): AsyncFuture<Void>

    /**
     * Install the uncaught exception handler of the justtrack SDK as the default
     * uncaught exception handler. If you already had a default uncaught exception
     * handler set, this will wrap your handler and eventually call it was well.
     */
    fun installUncaughtExceptionHandler()

    /**
     * The SDK starts tracking the user only after the start method is called.
     * User's unique IDs, install and app events will only be tracked after the method is used.
     * Any event preceding the start call will not be reported by the SDK.
     */
    fun start()

    /**
     * Check if the SDK was already started and hasn't been stopped yet.
     *
     * @return A boolean whether the SDK is tracking or not.
     */
    val isRunning: Boolean

    /**
     * The SDK stops tracking the user entirely.
     * No ID's or app events will be tracked or reported.
     * Any app events collected before the stop method is called will still be reported.
     */
    fun stop()

    /**
     * Instruct the justtrack backend to delete identifying information about the user.
     *
     * @return A future which resolves as soon as the backend was notified.
     */
    fun anonymize(): AsyncFuture<Boolean>?

    /**
     * Integrates the provided adapter with the SDK.
     *
     * @param adapter The IntegrationAdapter to add.
     */
    fun integrateWith(adapter: IntegrationAdapter)

    /**
     * Forward an IAP of a product to the justtrack backend. The backend will validate the purchase
     * and display the generated revenue for your app.
     *
     * @param productId The inapp product SKU (for example, 'com.some.thing.inapp1').
     * @param token     The token provided when the inapp product was purchased.
     * @param money     The amount of revenue generated from this purchase.
     * @return true if the purchase was stored successfully to the justtrack SDK. false if the purchase
     * contained invalid data.
     */
    fun forwardInApp(productId: String, token: String, money: Money): Boolean

    /**
     * Forward an IAP of a subscription to the justtrack backend. The backend will validate the purchase
     * and display the generated revenue for your app.
     *
     * @param productId The purchased subscription ID (for example, 'monthly001').
     * @param token          The token provided when the subscription was purchased.
     * @param money          The amount of revenue generated from this purchase.
     * @return true if the purchase was stored successfully to the justtrack SDK. false if the purchase
     * contained invalid data.
     */
    fun forwardSubscription(productId: String, token: String, money: Money): Boolean
}
