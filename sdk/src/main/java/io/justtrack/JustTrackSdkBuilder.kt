package io.justtrack

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.CompositeLogger
import io.justtrack.log.Logger
import io.justtrack.versions.ApplicationVersionImpl
import io.justtrack.versions.SdkVersion
import io.justtrack.versions.SdkVersionImpl

/**
 * Use this class to build your instance of the SDK.
 */
@Suppress("TooManyFunctions")
class JustTrackSdkBuilder(
    /** The [Application] instance this SDK builder was created from. */
    val application: Application,
    /** The launch [Intent], used to extract deep-link and retargeting parameters. May be null. */
    val intent: Intent?,
    apiToken: String,
) : SdkBuilder {
    /** The API token used to authenticate with the justtrack backend. */
    var apiToken: String? = null
        private set

    private var customLogger: Logger? = null

    internal var logger = lazy {
        val defaultLogger: Logger = LoggerImpl(isLogEnabled)
        CompositeLogger(defaultLogger, customLogger)
    }
        @JvmName("getLogger")
        get

    /** Delay in seconds before retrying a failed attribution request. */
    var attributionRetryDelaySeconds: Long = 0
        private set

    /** Whether SDK callbacks should be executed serially rather than concurrently. */
    var runCallbacksSerially: Boolean = false
        private set

    /** Whether the SDK installs an uncaught-exception handler for crash reporting. */
    var isEnableUncaughtExceptionHandler: Boolean
        private set

    /** Whether the SDK should defer starting until [JustTrackSdk.start] is called manually. */
    var isManualStart: Boolean = false
        private set

    @get:JvmName("getPackageName")
    internal var packageName: String
        private set

    @get:JvmName("getApplicationVersion")
    internal var applicationVersion: ApplicationVersion
        private set

    @get:JvmName("isLogEnabled")
    internal var isLogEnabled: Boolean = false

    @get:JvmName("getIntegrationAdapters")
    internal val integrationAdapters = ArrayList<IntegrationAdapter>()

    @set:JvmName("setInstallReferrerDetailBundle")
    @get:JvmName("getInstallReferrerDetailBundle")
    internal var installReferrerDetailBundle: Bundle? = null

    @get:JvmName("getEnvironment")
    internal var environment: Environment = Environment()

    @get:JvmName("getReAttributionConfig")
    internal val reAttributionConfig: ReAttributionConfig

    @get:JvmName("getStartConfigBuilder")
    internal val startConfigBuilder: JustTrackSdkConfig.Builder

    @get:JvmName("getDeviceInfo")
    internal val deviceInfo: DeviceInfo

    @get:JvmName("getSdkVersion")
    internal var sdkVersion: SdkVersion

    /**
     * Initialize a new builder from the current activity.
     *
     * @param currentActivity The current active activity.
     * @param apiToken        The API token provided for your client.
     */
    constructor(currentActivity: Activity, apiToken: String) : this(currentActivity.application, currentActivity.intent, apiToken)

    /**
     * Initialize a new builder from your application. If possible, use [JustTrackSdkBuilder.JustTrackSdkBuilder] instead.
     *
     * @param application A reference to your [Application] (or something extending it).
     * @param apiToken    The API token provided for your client.
     */
    constructor(application: Application, apiToken: String) : this(application, null, apiToken)

    /**
     * Initialize a new builder with all required parameters.
     *
     * @param application A reference to your [Application] (or something extending it).
     * @param intent      The intent the app was launched on. Should be the result of [Activity.getIntent].
     * @param apiToken    The API token provided for your client.
     */
    init {
        this.apiToken = apiToken
            .removePrefix("sandbox-")
            .removePrefix("prod-")

        this.startConfigBuilder = JustTrackSdkConfig.Builder()

        this.reAttributionConfig = ReAttributionConfig()
        this.isEnableUncaughtExceptionHandler = true
        this.sdkVersion = SdkVersionImpl(
            BuildConfig.VERSION_MAJOR,
            BuildConfig.VERSION_MINOR,
            BuildConfig.VERSION_PATCH,
            BuildConfig.VERSION_NAME,
            PlatformType.ANDROID,
        )
        this.deviceInfo = DeviceInfoImpl(application)
        this.applicationVersion = deviceInfo.getAppVersion()
        this.packageName = deviceInfo.getApplicationPackageName()
    }

    override fun setPackageName(packageName: String): SdkBuilder {
        this.packageName = packageName

        return this
    }

    override fun setApplicationVersion(versionName: String, versionCode: String): SdkBuilder {
        this.applicationVersion = ApplicationVersionImpl(versionName, versionCode)

        return this
    }

    override fun setManualStart(isManual: Boolean): SdkBuilder {
        isManualStart = isManual

        return this
    }

    override fun setLogger(logger: Logger): SdkBuilder {
        this.customLogger = logger

        return this
    }

    @Throws(InvalidFieldException::class)
    override fun setTrackingId(trackingId: String?, trackingProvider: String): SdkBuilder {
        startConfigBuilder.withTrackingId(trackingId, trackingProvider)

        return this
    }

    override fun setAutomaticInAppPurchaseTracking(enabled: Boolean): SdkBuilder {
        startConfigBuilder.withAutomaticInAppPurchaseTracking(enabled)

        return this
    }

    @Throws(InvalidFieldException::class)
    override fun setUserId(userId: String): SdkBuilder {
        startConfigBuilder.withUserId(userId)

        return this
    }

    @Throws(InvalidFieldException::class)
    override fun setFirebaseAppInstanceId(firebaseAppInstanceId: String): SdkBuilder {
        startConfigBuilder.withFirebaseIntegration(firebaseAppInstanceId)

        return this
    }

    override fun setEnableBroadcastReceiver(enabled: Boolean): SdkBuilder = this

    override fun setInactivityTimeFrame(inactivityTimeFrameHours: Long): SdkBuilder {
        reAttributionConfig.setInactivityTimeFrameHours(inactivityTimeFrameHours)

        return this
    }

    override fun setReAttributionTimeFrame(reAttributionTimeFrameDays: Long): SdkBuilder {
        reAttributionConfig.setReAttributionTimeFrameDays(reAttributionTimeFrameDays)

        return this
    }

    override fun setReFetchReAttributionDelaySeconds(reFetchReAttributionDelaySeconds: Long): SdkBuilder {
        reAttributionConfig.reFetchReAttributionDelaySeconds = reFetchReAttributionDelaySeconds

        return this
    }

    override fun setAttributionRetryDelaySeconds(attributionRetryDelaySeconds: Long): SdkBuilder {
        this.attributionRetryDelaySeconds = attributionRetryDelaySeconds

        return this
    }

    override fun setInstallUncaughtExceptionHandler(installHandler: Boolean): SdkBuilder {
        this.isEnableUncaughtExceptionHandler = installHandler
        return this
    }

    override fun addIntegrationAdapters(adapters: List<IntegrationAdapter>): SdkBuilder {
        this.integrationAdapters.addAll(adapters)
        return this
    }

    override fun setLoggingEnabled(isEnabled: Boolean): SdkBuilder {
        this.isLogEnabled = isEnabled
        return this
    }

    override fun setServerUrl(serverUrl: String): SdkBuilder {
        this.environment = Environment(serverUrl)
        return this
    }

    override fun setPlatformType(platformType: PlatformType?): SdkBuilder {
        this.sdkVersion = SdkVersionImpl(
            BuildConfig.VERSION_MAJOR,
            BuildConfig.VERSION_MINOR,
            BuildConfig.VERSION_PATCH,
            BuildConfig.VERSION_NAME,
            platformType ?: PlatformType.ANDROID,
        )
        return this
    }

    /**
     * Configures all callbacks performed by the SDK to be executed serially. We use this method from
     * Unity to schedule the callbacks to happen in a serialized manner, ensuring they occur one after
     * the other instead of concurrently from different threads.
     *
     *
     * This serialization is implemented to address a crash observed with Unity when calling many callbacks
     * from Java to C# from different threads. By serializing all callbacks, they are guaranteed to occur
     * one at a time. However, it's essential to keep these callbacks short on the C# side to prevent
     * blocking the execution on the main thread (though we already introduce a short delay for callbacks
     * to the game, making this approach acceptable).
     */
    @Suppress("unused")
    fun runCallbacksSerially(): SdkBuilder {
        this.runCallbacksSerially = true

        return this
    }

    override fun build(): JustTrackSdk {
        return InstanceManager.getInstance(this)
    }
}
