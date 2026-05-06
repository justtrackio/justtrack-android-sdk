package io.justtrack

import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger

/**
 * A builder class you can use to instantiate your instance of the SDK.
 */
@Suppress("TooManyFunctions")
interface SdkBuilder {

    /**
     * Set the package name of your application.
     *
     * This allows the application developer to set their application package name that the SDK will associate with.
     * By default, the SDK will fetch the package name of the application automatically.
     *
     * @param packageName The package name to be associated with the SDK.
     * @return The builder instance for method chaining.
     */
    fun setPackageName(packageName: String): SdkBuilder

    /**
     * Set the application version for your application.
     *
     * This allows the application developer to set their application version that the SDK will associate with.
     * By default, the SDK will fetch the version of the application automatically.
     *
     * @param versionName The version name of the application (e.g., "1.0.0").
     * @param versionCode The version code of the application (e.g., "1").
     * @return The builder instance for method chaining.
     */
    fun setApplicationVersion(versionName: String, versionCode: String): SdkBuilder

    /**
     * Dictates the nature of the start method call.
     * If set to true then the start method will have to be called by app developers after justtrack SDK initialization.
     * If set to false, which is the default value, then the start method will be automatically called by justtrack SDK after initialization.
     *
     * @param isManual whether the sdk should start manually or not
     * @return The builder so you can chain methods if you want.
     */
    fun setManualStart(isManual: Boolean): SdkBuilder

    /**
     * Add custom logger to the SDK.
     * This logger will be used in addition to the SDK's default logger implementation.
     *
     * @param logger The logger to use.
     * @return The builder so you can chain methods if you want.
     */
    fun setLogger(logger: Logger): SdkBuilder

    /**
     * Set the tracking id the SDK will send to the backend.
     *
     *
     * The tracking id and provider must be shorter than 4096 characters and consist only of printable ASCII
     * characters (U+0020 to U+007E).
     *
     * @param trackingId       The tracking id the SDK will send to the backend.
     * @param trackingProvider The tracking provider which supplied the trackingId.
     * @return The builder so you can chain methods if you want.
     * @throws InvalidFieldException If the tracking id or tracking provider were set to invalid values.
     */
    @Throws(InvalidFieldException::class)
    fun setTrackingId(trackingId: String?, trackingProvider: String): SdkBuilder

    /**
     * Configure whether the SDK registers a [android.content.BroadcastReceiver] to integrate
     * with other justtrack libraries. This is enabled by default, but you can disable it if you
     * know that registering such a receiver is not allowed (e.g., because your app is currently in the
     * background).
     *
     *
     * If a [android.content.BroadcastReceiver] can not be registered by the SDK, an error will
     * be logged and the integration with other justtrack libraries is slightly degraded.
     *
     * @param enabled True if the SDK should try to register a [android.content.BroadcastReceiver].
     * @return The builder so you can chain methods if you want.
     */
    @Deprecated("setEnableBroadcastReceiver is deprecated and will be removed in a future release.")
    fun setEnableBroadcastReceiver(enabled: Boolean): SdkBuilder

    /**
     * The justtrack SDK can automatically track in-app product and subscription purchases and
     * forward them to the justtrack backend. It is enabled by default, but this method allows you
     * to configure the automation for your needs.
     *
     * @param enabled Set this to true to automatically forward in-app product and subscription purchases.
     * @return The builder so you can chain methods if you want.
     */
    fun setAutomaticInAppPurchaseTracking(enabled: Boolean): SdkBuilder

    /**
     * Forward a user id to the justtrack backend upon SDK init.
     *
     *
     * The user id must be shorter than 4096 characters and consist only of printable ASCII
     * characters (U+0020 to U+007E).
     *
     * @param userId The user id to forward.
     * @return The builder so you can chain methods if you want.
     * @throws InvalidFieldException If the user id was set to an invalid value.
     */
    @Throws(InvalidFieldException::class)
    fun setUserId(userId: String): SdkBuilder

    /**
     * Forwards the Firebase App Instance ID to the justtrack backend during SDK initialization.
     * <p>
     * This method is only required if you are manually configuring the SDK. If you are using
     * {@link JustTrackSdk#integrateWith} (see {@link JustTrackSdk#integrateWith}), then you do not need to call this method yourself.
     * <p>
     * The Firebase App Instance ID must:
     * <ul>
     *   <li>Be between 8 and 256 characters long</li>
     *   <li>Contain only printable ASCII characters (U+0020 to U+007E)</li>
     * </ul>
     * <p>
     * See the
     * <a href="https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics#getAppInstanceId()">
     * Firebase documentation</a> for how to obtain the App Instance ID.
     *
     * @param firebaseAppInstanceId The Firebase App Instance ID to forward.
     * @return This builder instance for method chaining.
     * @throws InvalidFieldException If the provided ID is invalid.
     */
    @Throws(InvalidFieldException::class)
    fun setFirebaseAppInstanceId(firebaseAppInstanceId: String): SdkBuilder

    /**
     * Specify the time frame after which a user is considered to be inactive. Default: 48h.
     *
     *
     * An inactive user did not open the app for this time frame and will check for a new attribution
     * on the next app open. Thus, by default a user is inactive if they don't open the app at least
     * every other day.
     *
     * @param inactivityTimeFrameHours After how many hours will a user be inactive.
     * @return The builder so you can chain methods if you want.
     */
    fun setInactivityTimeFrame(inactivityTimeFrameHours: Long): SdkBuilder

    /**
     * Specify the time frame after which a user is considered to be eligible for re-attribution. Default: 14 days.
     *
     *
     * A user eligible for re-attribution will fetch an attribution on the next app open. Thus, every
     * active user will by default check every two weeks for a new attribution.
     *
     * @param reAttributionTimeFrameDays After how many days will a user be eligible for re-attribution.
     * @return The builder so you can chain methods if you want.
     */
    fun setReAttributionTimeFrame(reAttributionTimeFrameDays: Long): SdkBuilder

    /**
     * Specify the number of seconds until the SDK fetches the attribution again after a re-attribution opportunity was detected.
     *
     *
     * Set to a negative number to deactivate fetching the attribution again.
     *
     * @param reFetchReAttributionDelaySeconds The number of seconds until the attribution is fetched a second time.
     * @return The builder so you can chain methods if you want.
     */
    fun setReFetchReAttributionDelaySeconds(reFetchReAttributionDelaySeconds: Long): SdkBuilder

    /**
     * Specify the number of seconds the SDK waits between tries to get an attribution after a network failure.
     *
     * @param attributionRetryDelaySeconds The number of seconds until another try is made to fetch an attribution.
     * @return The builder so you can chain methods if you want.
     */
    fun setAttributionRetryDelaySeconds(attributionRetryDelaySeconds: Long): SdkBuilder

    /**
     * Specify whether the justtrack SDK should install a handler for uncaught exceptions.
     * The handler installed by the justtrack SDK will forward any uncaught exception to
     * the previous default uncaught exception handler.
     *
     *
     * This is enabled by default. If you disable this, you can install the same handler
     * manually by calling [JustTrackSdk.installUncaughtExceptionHandler].
     *
     * @param installHandler Whether we should install the justtrack uncaught exception handler.
     * @return The builder so you can chain methods if you want.
     */
    fun setInstallUncaughtExceptionHandler(installHandler: Boolean): SdkBuilder

    /**
     * Specify which platform the SDK is running on. By default the platformType is set to Android.
     *
     * @param platformType ANDROID, UNITY, REACT_NATIVE
     * @return The builder so you can chain methods if you want.
     */
    fun setPlatformType(platformType: PlatformType?): SdkBuilder

    /**
     * Integrates the provided adapters with the SDK.
     *
     * @param adapters The list of IntegrationAdapters to add.
     * @return The SdkBuilder instance for method chaining.
     */
    fun addIntegrationAdapters(adapters: List<IntegrationAdapter>): SdkBuilder

    /**
     * Decide whether the SDK should produce logs in logcat.
     *
     * @param isEnabled whether the SDK should display logs in the console.
     * @return The SdkBuilder instance for method chaining.
     */
    fun setLoggingEnabled(isEnabled: Boolean): SdkBuilder

    /**
     * Set the server URL for the SDK to query.
     *
     * @param serverUrl The server URL with scheme. e.g. https://justtrack.io
     * @return The SdkBuilder instance for method chaining.
     */
    fun setServerUrl(serverUrl: String): SdkBuilder

    /**
     * Build a new instance of the SDK. You should not use the builder after calling this method.
     *
     * @return A fresh instance of the SDK.
     * @throws IllegalArgumentException Thrown if the SDK could not be build because a parameter
     * provided to the builder was invalid.
     */
    fun build(): JustTrackSdk
}
