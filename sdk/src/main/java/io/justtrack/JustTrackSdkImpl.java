package io.justtrack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.appset.AppSetIdInfo;
import com.google.android.play.core.integrity.StandardIntegrityManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.justtrack.ads.AdImpression;
import io.justtrack.api.AttributionApi;
import io.justtrack.api.AttributionApiImpl;
import io.justtrack.api.ConfigApi;
import io.justtrack.api.ConfigApiImpl;
import io.justtrack.api.EventApi;
import io.justtrack.api.EventApiImpl;
import io.justtrack.api.ExperimentApi;
import io.justtrack.api.ExperimentApiImpl;
import io.justtrack.api.HeaderProvider;
import io.justtrack.api.HeaderProviderImpl;
import io.justtrack.api.IntegrityApi;
import io.justtrack.api.IntegrityApiImpl;
import io.justtrack.api.LogApi;
import io.justtrack.api.LogApiImpl;
import io.justtrack.api.PrivacyApi;
import io.justtrack.api.PrivacyApiImpl;
import io.justtrack.attribution.AdvertiserIdInfo;
import io.justtrack.attribution.Attribution;
import io.justtrack.config.RemoteConfig;
import io.justtrack.config.RemoteConfigImpl;
import io.justtrack.crashes.ANRDetector;
import io.justtrack.crashes.ANRDetectorImpl;
import io.justtrack.crashes.CrashHandler;
import io.justtrack.crashes.CrashReportNativeLoaderImpl;
import io.justtrack.crashes.CrashReportingFactory;
import io.justtrack.crashes.CrashReportingFactoryImpl;
import io.justtrack.crashes.StacktraceProviderImpl;
import io.justtrack.deeplinks.DeepLinkData;
import io.justtrack.deeplinks.DeepLinkListener;
import io.justtrack.events.Dimension;
import io.justtrack.events.JtDeeplinkHandledEvent;
import io.justtrack.events.JtDeeplinkNotHandledEvent;
import io.justtrack.events.Money;
import io.justtrack.events.PublishEventTaskExecutor;
import io.justtrack.events.PublishEventTaskExecutorImpl;
import io.justtrack.events.RevenueForwarder;
import io.justtrack.events.RevenueForwarderImpl;
import io.justtrack.exceptions.SdkNotTrackingException;
import io.justtrack.executor.SerializeHandlerThreadImpl;
import io.justtrack.executor.TaskExecutor;
import io.justtrack.executor.TaskExecutorImpl;
import io.justtrack.integrations.IntegrationAdapter;
import io.justtrack.log.Logger;
import io.justtrack.providers.AdvertiserIdProvider;
import io.justtrack.providers.AdvertiserIdProviderImpl;
import io.justtrack.retargeting.PreliminaryRetargetingParameters;
import io.justtrack.retargeting.PreliminaryRetargetingParametersListener;
import io.justtrack.retargeting.RetargetingParameters;
import io.justtrack.retargeting.RetargetingParametersListener;
import io.justtrack.util.ExecutorServiceFactory;
import io.justtrack.util.ExecutorServiceFactoryImpl;
import io.justtrack.util.FileAccessorImpl;
import io.justtrack.util.InstallerSourceIdProvider;
import io.justtrack.util.InstallerSourceIdProviderImpl;
import io.justtrack.versions.SdkVersion;
import io.justtrack.versions.VersionBundle;
import kotlin.jvm.JvmName;

class JustTrackSdkImpl implements JustTrackSdk {
    private static final long EVENT_WAIT_TIME = 5_000L;
    @NonNull
    final Context context;
    @NonNull
    final HttpLogger logger;
    @NonNull
    final CrashHandler crashHandler;
    @NonNull
    final ANRDetector anrDetector;
    @NonNull
    final AttributionIdManager attributionIdManager;
    @NonNull
    final PublishEventsQueue publishEventsQueue;
    @NonNull
    final IntegrityTokenPublisher integrityTokenPublisher;
    @NonNull
    final DeviceInfo deviceInfo;
    @Nullable
    private PreliminaryRetargetingParametersImpl preliminaryRetargetingParametersImpl;
    @VisibleForTesting
    @NonNull
    final AttributionOutputProvider attributionOutputProvider;
    @Nullable
    private Intent intent;
    @Nullable
    String trackingId = null;
    @NonNull
    String trackingProvider = "advertiserId";
    private boolean handleIntentsOnResume;
    @VisibleForTesting
    @NonNull
    final AppVersionProvider appVersionProvider;
    @NonNull
    private final AdvertiserIdProvider advertiserIdProvider;
    @VisibleForTesting
    @NonNull
    final UserIdProvider userIdProvider;
    @NonNull
    private final AppSetIdProvider appSetIdProvider;
    @NonNull
    private final IntegrityTokenProvider integrityTokenProvider;
    @NonNull
    private final IntegritySecretProvider integritySecretProvider;
    @NonNull
    private final RemoteConfig remoteConfig;
    @NonNull
    private final SubscriptionManager<AttributionListener> attributionSubscriptions;
    @NonNull
    private final SubscriptionManager<RetargetingParametersListener> retargetingParametersSubscriptions;
    @NonNull
    private final SubscriptionManager<PreliminaryRetargetingParametersListener> preliminaryRetargetingParametersSubscriptions;
    @NonNull
    private final SubscriptionManager<DeepLinkListener> deepLinkSubscriptions;
    @NonNull
    private final SessionManager sessionManager;
    @NonNull
    private final DatabaseInterface databaseInterface;
    @NonNull
    private final SdkConfigDelegate sdkConfigDelegate;
    @NonNull
    private final NetworkErrorLogger networkErrorLogger;
    @NonNull
    private final AtomicBoolean isTracking = new AtomicBoolean(false);
    @NonNull
    private final TrackingStateManager trackingStateManager;
    @NonNull
    private final SdkLifecycleManager sdkLifecycleManager;
    @NonNull
    private final ReconnectHandler reconnectHandler;
    @NonNull
    private final ExperimentVariantHandler experimentVariantHandler;
    @NonNull
    private final JustTrackSdkConfig startConfig;
    @VisibleForTesting
    @NonNull
    final List<IntegrationAdapter> pendingIntegrationAdapters = new ArrayList<>();
    @NonNull
    private final VersionBundle versionBundle;
    private final List<IntegrationAdapter> integratedAdapters = new ArrayList<>();
    @NonNull
    private final CustomIdManager customIdManager;
    @NonNull
    private final GlobalDimensionsRepo globalDimensionsRepo;
    @NonNull
    private final AttributionApi attributionApi;
    @NonNull
    private final EventApi eventApi;
    @NonNull
    private final ExperimentApi experimentApi;
    @NonNull
    private final IntegrityApi integrityApi;
    @NonNull
    private final PrivacyApi privacyApi;
    @VisibleForTesting
    @NonNull
    final TaskExecutor taskExecutor;
    @NonNull
    private final RevenueForwarder revenueForwarder;
    @VisibleForTesting
    @NonNull
    final ClaimProvider resolvedClaimProvider;

    JustTrackSdkImpl(@NonNull JustTrackSdkBuilder builder) {
        this(
                builder,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RetryConfig.Companion.getDEFAULT_CONFIG(),
                null,
                builder.getStartConfigBuilder().build(),
                null
        );
    }

    JustTrackSdkImpl(
            @NonNull JustTrackSdkBuilder builder,
            @Nullable AttributionApi attributionApi,
            @Nullable ConfigApi configApi,
            @Nullable EventApi eventApi,
            @Nullable ExperimentApi experimentApi,
            @Nullable IntegrityApi integrityApi,
            @Nullable LogApi logApi,
            @Nullable PrivacyApi privacyApi,
            @NonNull RetryConfig retryConfig,
            @Nullable ClaimProvider claimProvider,
            @NonNull JustTrackSdkConfig sdkConfig,
            @Nullable StandardIntegrityManager.StandardIntegrityTokenProvider standardIntegrityTokenProvider
    ) {
        this(
                builder.getApplication(),
                builder.getPackageName(),
                builder.getApiToken(),
                builder.getIntent(),
                new ExecutorServiceFactoryImpl(),
                DatabaseInterface.getInstance(builder.getApplication(), builder.getLogger().getValue()),
                builder.getApplicationVersion(),
                builder.getSdkVersion(),
                builder.getLogger().getValue(),
                builder.getDeviceInfo(),
                builder.getEnvironment(),
                builder.isLogEnabled(),
                attributionApi,
                configApi,
                eventApi,
                experimentApi,
                integrityApi,
                logApi,
                privacyApi,
                retryConfig,
                claimProvider,
                sdkConfig,
                standardIntegrityTokenProvider,
                builder.getRunCallbacksSerially(),
                builder.getIntegrationAdapters(),
                builder.getInstallReferrerDetailBundle(),
                builder.getReAttributionConfig(),
                builder.getAttributionRetryDelaySeconds(),
                builder.isEnableConnectionTracking()
        );
    }

    JustTrackSdkImpl(@NonNull Context application,
                     @NonNull String packageName,
                     @NonNull String apiToken,
                     @Nullable Intent intent,
                     @NonNull ExecutorServiceFactory executorBuilder,
                     @NonNull DatabaseInterface databaseInterface,
                     @NonNull ApplicationVersion applicationVersion,
                     @NonNull SdkVersion sdkVersion,
                     @NonNull Logger fallbackLogger,
                     @NonNull DeviceInfo deviceInfo,
                     @NonNull Environment environment,
                     @NonNull Boolean isLogEnabled,
                     @Nullable AttributionApi attributionApi,
                     @Nullable ConfigApi configApi,
                     @Nullable EventApi eventApi,
                     @Nullable ExperimentApi experimentApi,
                     @Nullable IntegrityApi integrityApi,
                     @Nullable LogApi logApi,
                     @Nullable PrivacyApi privacyApi,
                     @NonNull RetryConfig retryConfig,
                     @Nullable ClaimProvider claimProvider,
                     @NonNull JustTrackSdkConfig sdkConfig,
                     @Nullable StandardIntegrityManager.StandardIntegrityTokenProvider standardIntegrityTokenProvider,
                     @NonNull Boolean runCallbackSerially,
                     @NonNull List<IntegrationAdapter> adapters,
                     @Nullable Bundle installReferrerDetailBundle,
                     @NonNull ReAttributionConfig reAttributionConfig,
                     @NonNull Long attributionRetryDelaySeconds,
                     @NonNull Boolean enableConnectionTracking
    ) {
        this.context = application;
        this.databaseInterface = databaseInterface;
        this.networkErrorLogger = new NetworkErrorLogger();
        this.startConfig = sdkConfig;
        this.versionBundle = new VersionBundle(sdkVersion, applicationVersion);
        LogAggregatorImpl logAggregator = new LogAggregatorImpl(
                new MessageRepositoryImpl(Formatter.INSTANCE, this.databaseInterface.openMessages(), fallbackLogger),
                new MetricRepositoryImpl(Formatter.INSTANCE, this.databaseInterface.openMetrics(), fallbackLogger),
                fallbackLogger,
                networkErrorLogger,
                isTracking
        );
        setTrackingProvider(sdkConfig);

        HttpClient httpClient = new HttpClientImpl(deviceInfo, fallbackLogger);
        HeaderProvider headerProvider = new HeaderProviderImpl(apiToken, packageName, deviceInfo, versionBundle);
        LogApi resolvedLogApi = (logApi != null) ? logApi : new LogApiImpl(httpClient, headerProvider, environment);
        this.logger = new HttpLoggerImpl(fallbackLogger, resolvedLogApi, versionBundle, logAggregator);

        this.attributionApi = (attributionApi != null) ? attributionApi : new AttributionApiImpl(httpClient, headerProvider, environment, logger);
        this.eventApi = (eventApi != null) ? eventApi : new EventApiImpl(httpClient, headerProvider, environment, logger);
        this.experimentApi = (experimentApi != null) ? experimentApi : new ExperimentApiImpl(httpClient, headerProvider, environment, logger);
        this.integrityApi = (integrityApi != null) ? integrityApi : new IntegrityApiImpl(httpClient, headerProvider, environment, logger);
        this.privacyApi = (privacyApi != null) ? privacyApi : new PrivacyApiImpl(httpClient, headerProvider, environment, logger);

        databaseInterface.setLogger(logger);

        this.deviceInfo = deviceInfo;
        JtCrashReporter crashReporter = new JtCrashReporter(context, new FileAccessorImpl(context), logger, Formatter.INSTANCE, isTracking);
        final CrashReportingFactory crashReportingFactory =
                new CrashReportingFactoryImpl(crashReporter);
        this.crashHandler = new CrashHandler(
                crashReportingFactory.create(),
                logger,
                isTracking,
                new CrashReportNativeLoaderImpl(packageName)
        );
        this.anrDetector = new ANRDetectorImpl(
                this.crashHandler::capturingException,
                new StacktraceProviderImpl()
        );


        this.logger.setBreadCrumbReporter(crashReporter);
        ExecutorService executor = executorBuilder.create(this.crashHandler);
        this.taskExecutor = new TaskExecutorImpl(executor, runCallbackSerially, new SerializeHandlerThreadImpl());

        FirebaseIdManager firebaseIdManager = new FirebaseIdManager(
                context,
                taskExecutor,
                this.attributionApi,
                this.deviceInfo,
                logger,
                networkErrorLogger
        );
        this.customIdManager = new CustomIdManager(
                context,
                taskExecutor,
                this.deviceInfo,
                this.attributionApi,
                logger,
                networkErrorLogger
        );

        this.attributionIdManager = new AttributionIdManager(
                taskExecutor,
                context,
                this.databaseInterface,
                customIdManager,
                firebaseIdManager
        );

        PeriodicLogsPublisher periodicLogsPublisher = new PeriodicLogsPublisher(this.logger);

        this.advertiserIdProvider = new AdvertiserIdProviderImpl(context, taskExecutor, logger);

        if (claimProvider != null) {
            resolvedClaimProvider = claimProvider;
        } else {
            resolvedClaimProvider = new ClaimProviderImpl(
                    this.deviceInfo,
                    this.advertiserIdProvider,
                    this.attributionApi,
                    retryConfig,
                    this.taskExecutor,
                    this.logger
            );
        }

        this.userIdProvider = new UserIdProviderImpl(
                taskExecutor,
                this.deviceInfo,
                packageName,
                new UserIdProviderImpl.AttributionParams(
                        attributionIdManager,
                        advertiserIdProvider,
                        trackingId
                ),
                logger
        );

        this.attributionSubscriptions = new SubscriptionManager<>();
        this.retargetingParametersSubscriptions = new SubscriptionManager<>();
        this.preliminaryRetargetingParametersSubscriptions = new SubscriptionManager<>();
        this.deepLinkSubscriptions = new SubscriptionManager<>();

        handleIntentsOnResume = true;
        if (intent == null) {
            Activity currentActivity = JustTrack.getCurrentActivity();
            if (currentActivity != null) {
                intent = currentActivity.getIntent();
            }
        }
        preliminaryRetargetingParametersImpl = PreliminaryRetargetingParametersImpl.fromIntent(intent);

        AttributionSubscriptionHandler attributionSubscriptionHandler = new AttributionSubscriptionHandlerImpl(
                attributionSubscriptions,
                logger,
                networkErrorLogger,
                taskExecutor
        );

        InstallReferrerProvider installReferrerProvider = new InstallReferrerReader(
                application,
                installReferrerDetailBundle,
                this.logger
        );

        InstallerSourceIdProvider installerSourceIdProvider = new InstallerSourceIdProviderImpl(
                context,
                this.deviceInfo,
                logger
        );
        this.attributionOutputProvider = new AttributionOutputProviderImpl(
                this.attributionApi,
                taskExecutor,
                this.databaseInterface,
                new AttributionOutputProviderImpl.AttributionParams(
                        attributionIdManager,
                        userIdProvider,
                        new ChainedReAttributionDecider(
                                new ResolveOrganicAttributionDecider(),
                                new IntentLaunchDecider(preliminaryRetargetingParametersImpl),
                                reAttributionConfig
                        ),
                        installReferrerProvider,
                        resolvedClaimProvider,
                        retryConfig,
                        trackingId,
                        trackingProvider,
                        advertiserIdProvider,
                        this::getAppSetId,
                        this::getIntegritySecret,
                        reAttributionConfig.getReFetchReAttributionDelaySeconds(),
                        attributionRetryDelaySeconds,
                        sdkConfig,
                        this.deviceInfo,
                        intent,
                        this.attributionIdManager,
                        installerSourceIdProvider,
                        preliminaryRetargetingParametersImpl
                ),
                new AttributionOutputProviderImpl.Subscribers(
                        attributionSubscriptionHandler,
                        this.retargetingParametersSubscriptions
                ),
                new AttributionOutputProviderImpl.Loggers(logger, networkErrorLogger),
                new VersionBundle(sdkVersion, applicationVersion)
        );

        PublishEventTaskExecutor publishEventTaskExecutor = new PublishEventTaskExecutorImpl(
                new PublishEventTaskExecutorImpl.AttributionParams(
                        attributionIdManager,
                        advertiserIdProvider,
                        trackingProvider,
                        trackingId,
                        this.deviceInfo,
                        versionBundle
                ),
                attributionOutputProvider,
                taskExecutor,
                retryConfig,
                this.eventApi,
                logger
        );
        this.globalDimensionsRepo = new GlobalDimensionsRepo(
                context.getSharedPreferences(
                        GlobalDimensionsRepo.STORE_NAME,
                        Context.MODE_PRIVATE
                )
        );
        ConnectivityProvider connectivityProvider = ConnectivityProvider.createProvider(application);
        this.publishEventsQueue = new PublishEventsQueue(
                publishEventTaskExecutor,
                logger,
                networkErrorLogger,
                new EventRepositoryImpl(
                        Formatter.INSTANCE,
                        versionBundle.getSdkVersion().getPlatformType(),
                        this.databaseInterface.openEvents(),
                        logger
                ),
                EVENT_WAIT_TIME,
                isTracking,
                sdkVersion,
                globalDimensionsRepo,
                enableConnectionTracking,
                connectivityProvider
        );

        this.appVersionProvider = new AppVersionProvider(taskExecutor, applicationVersion, databaseInterface);

        this.appSetIdProvider = new AppSetIdProvider();
        this.integritySecretProvider = new IntegritySecretProvider(taskExecutor, databaseInterface);
        this.integrityTokenProvider = new IntegrityTokenProvider(
                taskExecutor,
                context,
                deviceInfo,
                standardIntegrityTokenProvider,
                retryConfig.getIntegrityTokenRetries()
        );
        this.integrityTokenPublisher = new IntegrityTokenPublisher(
                taskExecutor,
                deviceInfo,
                retryConfig.getIntegrityTokenRetries()
        );
        SdkFirstInitializationTimestampRepo sdkFirstInitializationTimestampRepo = new SdkFirstInitializationTimestampRepo(
                context.getSharedPreferences(
                        SdkFirstInitializationTimestampRepo.STORE_NAME,
                        Context.MODE_PRIVATE
                )
        );
        ConfigApi resolvedConfigApi = (configApi != null) ? configApi : new ConfigApiImpl(httpClient, headerProvider, environment, logger);
        this.remoteConfig = new RemoteConfigImpl(
                context,
                isTracking,
                new RemoteConfigImpl.AttributionParams(
                        this::getInstallInstanceIdInternal,
                        userIdProvider,
                        advertiserIdProvider,
                        databaseInterface,
                        deviceInfo,
                        sdkVersion
                ),
                sdkFirstInitializationTimestampRepo,
                taskExecutor,
                resolvedConfigApi,
                logger
        );

        this.sessionManager = new SessionManagerImpl(publishEventsQueue, crashHandler, context, isTracking);
        this.revenueForwarder = new RevenueForwarderImpl(publishEventsQueue, sessionManager, isTracking, logger);

        synchronized (pendingIntegrationAdapters) {
            this.pendingIntegrationAdapters.addAll(adapters);
        }

        this.sdkConfigDelegate = new SdkConfigDelegate(firebaseIdManager, attributionIdManager);

        WorkerScheduler workerScheduler = new WorkerScheduler(
                new WorkerScheduler.Config(
                        context,
                        apiToken,
                        sdkVersion,
                        isTracking,
                        logger,
                        isLogEnabled,
                        new WorkerScheduler.ApplicationInfo(
                                packageName,
                                applicationVersion
                        ),
                        environment,
                        advertiserIdProvider,
                        trackingId,
                        trackingProvider,
                        userIdProvider
                ),
                this::getInstallInstanceIdInternal
        );
        BillingTracker billingTracker = BillingTrackerFactory.getBillingClient(this, context, logger);
        this.trackingStateManager = new TrackingStateManager(
                isTracking,
                new TrackingStateManager.Dependencies(
                        context,
                        logger,
                        taskExecutor,
                        deviceInfo,
                        this.privacyApi,
                        userIdProvider,
                        advertiserIdProvider,
                        attributionIdManager
                ),
                new TrackingStateManager.Components(
                        sdkFirstInitializationTimestampRepo,
                        sdkConfigDelegate,
                        sessionManager,
                        attributionOutputProvider,
                        crashReporter,
                        workerScheduler,
                        billingTracker
                ),
                new TrackingStateManager.Integrations(
                        pendingIntegrationAdapters,
                        integratedAdapters
                ),
                new TrackingStateManager.Suppliers(
                        this::publishIntegrityToken,
                        this::getInstallInstanceIdInternal,
                        this::setUserIdInternal
                )
        );

        this.reconnectHandler = new ReconnectHandler(
                context,
                logger,
                attributionOutputProvider,
                new ReconnectHandler.IdManagers(
                        customIdManager,
                        firebaseIdManager,
                        userIdProvider,
                        attributionIdManager,
                        advertiserIdProvider
                )
        );

        this.experimentVariantHandler = new ExperimentVariantHandler(
                new ExperimentVariantHandler.Dependencies(
                        taskExecutor,
                        deviceInfo,
                        this.experimentApi,
                        logger,
                        isTracking,
                        appVersionProvider,
                        versionBundle.getSdkVersion()
                ),
                new ExperimentVariantHandler.AttributionParams(
                        this::getInstallInstanceIdInternal,
                        userIdProvider::provideUserIdFuture,
                        advertiserIdProvider
                )
        );

        this.sdkLifecycleManager = new SdkLifecycleManager(
                new SdkLifecycleManager.Dependencies(
                        context,
                        logger,
                        taskExecutor,
                        executor,
                        sessionManager,
                        connectivityProvider,
                        publishEventsQueue,
                        periodicLogsPublisher,
                        anrDetector,
                        workerScheduler,
                        networkErrorLogger,
                        isTracking,
                        appVersionProvider
                ),
                new SdkLifecycleManager.Callbacks(
                        reconnectHandler::onReconnect,
                        (reason) -> {
                            reconnectHandler.retrySendPersistId(reason);
                            return kotlin.Unit.INSTANCE;
                        },
                        (newIntent, isAutomatic) -> {
                            handleNewIntent(newIntent, isAutomatic);
                            return kotlin.Unit.INSTANCE;
                        },
                        () -> InstanceManager.clearInstance(this)
                )
        );

    }


    void init(@NonNull JustTrackSdkBuilder builder) {
        sdkLifecycleManager.init(builder.getApplication(), this);
    }

    @Override
    public void start() {
        trackingStateManager.startWithConfig(startConfig, this);
    }

    @VisibleForTesting
    void startWithConfig(@NonNull JustTrackSdkConfig config) {
        setTrackingProvider(config);
        trackingStateManager.startWithConfig(config, this);
    }

    private void setTrackingProvider(JustTrackSdkConfig config) {
        trackingProvider = config.trackingIdProvider;
        if (config.trackingId != null) {
            trackingId = config.trackingId;
        }
    }

    @Override
    public boolean isRunning() {
        return trackingStateManager.isRunning();
    }

    @Override
    public void stop() {
        trackingStateManager.stop();
    }

    @Override
    public AsyncFuture<Boolean> anonymize() {
        return trackingStateManager.anonymize();
    }

    @NonNull
    @Override
    public AsyncFuture<Attribution> getAttribution() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return new TransformingFuture<>(attributionOutputProvider.provideAttributionOutput(null), AttributionOutput::getAttribution);
    }

    @Override
    @NonNull
    public AsyncFuture<AdvertiserIdInfo> getAdvertiserIdInfo() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return advertiserIdProvider.provideAdvertiserId();
    }

    @Override
    public void integrateWith(@NonNull IntegrationAdapter adapter) {
        if (!isRunning()) {
            synchronized (pendingIntegrationAdapters) {
                pendingIntegrationAdapters.add(adapter);
            }
        } else {
            integratedAdapters.add(adapter);
            adapter.integrate(context, this, logger);
        }
    }

    @NonNull
    @Override
    public AsyncFuture<Void> setExperimentVariant(@NonNull String experiment, @NonNull String variant, @Nullable Date happenedAt) {
        return experimentVariantHandler.setExperimentVariant(experiment, variant, new ArrayList<>(), happenedAt);
    }

    @NonNull
    @Override
    public AsyncFuture<Void> setExperimentVariant(
            @NonNull String experiment,
            @NonNull String variant,
            @NonNull List<String> tags,
            @Nullable Date happenedAt
    ) {
        return experimentVariantHandler.setExperimentVariant(experiment, variant, tags, happenedAt);
    }

    @Override
    public void onNewIntent(@Nullable Intent newIntent) {
        taskExecutor.execute(
                () -> handleNewIntent(newIntent, false),
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not handle new intent, SDK is shutting down"
                    );
                }
        );
    }

    @NonNull
    @Override
    public Subscription registerDeepLinkListener(@NonNull DeepLinkListener deepLinkListener) {
        if (intent != null) {
            Uri deepLinkUri = intent.getData();
            if (deepLinkUri != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
                taskExecutor.execute(
                        () -> performDeepLinkCall(new DeepLinkDataImpl(deepLinkUri), deepLinkListener),
                        exception -> logger.warn("Could not call deep link subscription, SDK is shutting down", exception),
                        false
                );
            }
        }

        return deepLinkSubscriptions.subscribe(deepLinkListener);
    }

    @Override
    public void shutdown() {
        sdkLifecycleManager.shutdown();
    }

    @NonNull
    @Override
    public Subscription registerAttributionListener(@NonNull AttributionListener attributionListener) {
        return attributionSubscriptions.subscribe(attributionListener);
    }

    @NonNull
    @Override
    public Subscription registerRetargetingParametersListener(
            @NonNull RetargetingParametersListener retargetingParametersListener) {
        return retargetingParametersSubscriptions.subscribe(retargetingParametersListener);
    }

    @NonNull
    @Override
    public Subscription registerPreliminaryRetargetingParametersListener(
            @NonNull PreliminaryRetargetingParametersListener preliminaryRetargetingParametersListener) {
        return preliminaryRetargetingParametersSubscriptions.subscribe(preliminaryRetargetingParametersListener);
    }

    @NonNull
    @Override
    public AsyncFuture<RetargetingParameters> getRetargetingParameters() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return new TransformingFuture<>(attributionOutputProvider.provideAttributionOutput(null), AttributionOutput::getRetargetingParameters);
    }

    @Nullable
    @Override
    public PreliminaryRetargetingParameters getPreliminaryRetargetingParameters() {
        return preliminaryRetargetingParametersImpl;
    }

    @Override
    public void installUncaughtExceptionHandler() {
        crashHandler.installUncaughtExceptionHandler();
    }

    @NonNull
    @Override
    public AsyncFuture<Void> publishEvent(@NonNull AppEvent event) {
        return publishEventsQueue.track(event, sessionManager);
    }

    @Override
    public @NonNull AsyncFuture<Void> track(@NonNull AppEvent event) {
        return publishEvent(event);
    }

    @Override
    public @NonNull AsyncFuture<Void> track(@NonNull String eventName, @NonNull Map<String, String> dimensions) {
        return track(new AppEvent(eventName, dimensions));
    }

    @Override
    public @NonNull AsyncFuture<Void> track(@NonNull String eventName) {
        return track(new AppEvent(eventName));
    }

    @NonNull
    @Override
    public AsyncFuture<Boolean> setUserId(@NonNull String userId) {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return setUserIdInternal(userId);
    }

    @Override
    @NonNull
    public AsyncFuture<Boolean> setFirebaseAppInstanceId(@NonNull String firebaseAppInstanceId) {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return setFirebaseAppInstanceIdInternal(firebaseAppInstanceId);
    }

    @Override
    @NonNull
    public AsyncFuture<Void> forwardAdImpression(@NonNull AdImpression adImpression) {
        return revenueForwarder.forwardAdImpression(adImpression);
    }

    @Override
    public void setGlobalDimension0(@Nullable String value) {
        setGlobalDimension(Dimension.JT_GLOBAL_0, value);
    }

    @Override
    public void setGlobalDimension1(@Nullable String value) {
        setGlobalDimension(Dimension.JT_GLOBAL_1, value);
    }

    @Override
    public void setGlobalDimension2(@Nullable String value) {
        setGlobalDimension(Dimension.JT_GLOBAL_2, value);
    }

    private void setGlobalDimension(@NonNull Dimension dimension, @Nullable String value) {
        if (!Validation.validDimensionValue(dimension.toString(), value)) {
            logger.warn("Ignoring invalid " + dimension + " value: value must be shorter than 4096 characters and consist of ISO 8859-1 characters");
            return;
        }
        globalDimensionsRepo.set(dimension, value);
    }

    @Override
    public boolean forwardInApp(@NonNull String productId, @NonNull String token, @NonNull Money totalPrice) {
        return revenueForwarder.forwardInApp(productId, token, totalPrice);
    }

    @Override
    public boolean forwardSubscription(@NonNull String subscriptionId, @NonNull String token, @NonNull Money subscriptionPrice) {
        return revenueForwarder.forwardSubscription(subscriptionId, token, subscriptionPrice);
    }

    @NonNull
    @Override
    public Version getSdkVersion() {
        return versionBundle.getSdkVersion();
    }

    @NonNull
    @Override
    public AsyncFuture<ApplicationVersion> getAppVersionAtInstall() {
        return appVersionProvider.getApplicationVersionAtInstalled();
    }

    @NonNull
    @Override
    public AsyncFuture<String> getInstallInstanceId() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return getInstallInstanceIdInternal();
    }

    @NonNull
    @Override
    public RemoteConfig getRemoteConfig() {
        return remoteConfig;
    }

    @Override
    public void setAutomaticInAppPurchaseTracking(boolean enabled) {
        trackingStateManager.setAutomaticInAppPurchaseTracking(enabled);
    }


    void onResume(@NonNull Activity activity) {
        sdkLifecycleManager.onResume(activity);
    }

    void onPause() {
        sdkLifecycleManager.onPause();
    }

    void notifyAppStart(@NonNull AppStartDuration startEvent) {
        sdkLifecycleManager.notifyAppStart(this, startEvent);
    }

    @NonNull
    private AsyncFuture<AppSetIdInfo> getAppSetId() {
        return appSetIdProvider.provideAppSetIdFuture(taskExecutor, context, logger);
    }

    @NonNull
    AsyncFuture<IntegrityTokenData> getIntegrityToken() {
        return integrityTokenProvider.getOrRenewFuture(
                logger,
                getInstallInstanceIdInternal(),
                getIntegritySecret(),
                databaseInterface,
                null
        );
    }

    @NonNull
    AsyncFuture<String> getIntegritySecret() {
        return integritySecretProvider.provideIntegritySecret(getInstallInstanceIdInternal());
    }

    @VisibleForTesting
    void callPreliminaryRetargetingParametersSubscriptions(
            @NonNull PreliminaryRetargetingParameters preliminaryRetargetingParameters) {
        preliminaryRetargetingParametersSubscriptions.call(subscription -> taskExecutor.execute(
                () -> subscription.onPreliminaryRetargetingParametersReceived(preliminaryRetargetingParameters),
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not call preliminary retargeting parameter subscription, SDK is shutting down"
                    );
                },
                false
        ));
    }

    private void callDeepLinkSubscriptions(@NonNull DeepLinkData deepLink) {
        deepLinkSubscriptions.call(subscription -> taskExecutor.execute(
                () -> performDeepLinkCall(deepLink, subscription),
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not call deep link subscription, SDK is shutting down"
                    );
                },
                false
        ));
    }

    private void performDeepLinkCall(@NonNull DeepLinkData deepLink, @NonNull DeepLinkListener subscription) {
        @NonNull String sessionId = sessionManager.getLatestSessionId();
        switch (subscription.onDeepLinkClicked(deepLink)) {
            case DEEP_LINK_HANDLED:
                publishEvent(new JtDeeplinkHandledEvent(sessionId, deepLink.getUri().toString(), new Date()));
                break;
            case DEEP_LINK_NOT_HANDLED:
                publishEvent(new JtDeeplinkNotHandledEvent(sessionId, deepLink.getUri().toString(), new Date()));
                break;
            case DEEP_LINK_IGNORED:
            default:
                // do nothing
                break;
        }
    }

    void handleNewIntent(@Nullable Intent newIntent, boolean isAutomatic) {
        Uri deepLinkUri = null;
        PreliminaryRetargetingParametersImpl oldPreliminaryRetargetingParametersImpl = null;
        PreliminaryRetargetingParametersImpl newPreliminaryRetargetingParametersImpl = null;

        // Deadlock-Safety: We only shuffle a few fields on our instance, any call to user-provided functions
        // is done at a later point without holding the lock.
        synchronized (this) {
            // if we automatically call this method, but were already once called by hand, ignore the call.
            // there might be a stale intent which got passed to us from {@link Activity#getIntent} which
            // we have to ignore
            if (isAutomatic && !handleIntentsOnResume) {
                return;
            }
            handleIntentsOnResume = isAutomatic;
            if (newIntent != null && newIntent != intent) {
                intent = newIntent;
                deepLinkUri = newIntent.getData();
                if (!Intent.ACTION_VIEW.equals(newIntent.getAction())) {
                    // this was not an intent from opening some deeplink
                    deepLinkUri = null;
                }
                oldPreliminaryRetargetingParametersImpl = preliminaryRetargetingParametersImpl;
                preliminaryRetargetingParametersImpl = PreliminaryRetargetingParametersImpl.fromIntent(intent);
                newPreliminaryRetargetingParametersImpl = preliminaryRetargetingParametersImpl;
            }
        }

        if (deepLinkUri != null) {
            callDeepLinkSubscriptions(new DeepLinkDataImpl(deepLinkUri));
        }
        if (oldPreliminaryRetargetingParametersImpl != null) {
            oldPreliminaryRetargetingParametersImpl
                    .reject(new RuntimeException("A new intent was received while processing the attribution, aborted"));
        }
        if (newPreliminaryRetargetingParametersImpl != null) {
            callPreliminaryRetargetingParametersSubscriptions(newPreliminaryRetargetingParametersImpl);
            attributionOutputProvider.provideAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION);
        }
    }

    @VisibleForTesting
    void onReconnect() {
        reconnectHandler.onReconnect();
    }

    @VisibleForTesting
    void retryAttributionAfterReconnect() {
        reconnectHandler.retryAttributionAfterReconnect();
    }

    @NonNull
    AsyncFuture<Boolean> setUserIdInternal(@NonNull String userId) {
        return customIdManager.setCustomUserId(
                userId,
                userIdProvider.provideUserIdFuture(),
                attributionIdManager,
                advertiserIdProvider
        );
    }

    @VisibleForTesting
    AsyncFuture<Boolean> publishIntegrityToken() {
        return integrityTokenPublisher.publishIntegrityTokenIfNotAlreadyRunning(
                logger,
                networkErrorLogger,
                this.integrityApi,
                databaseInterface,
                this::getIntegrityToken,
                getInstallInstanceIdInternal()
        );
    }

    @NonNull
    private AsyncFuture<Boolean> setFirebaseAppInstanceIdInternal(@NonNull String firebaseAppInstanceId) {
        return sdkConfigDelegate.setFirebaseAppInstanceId(
                firebaseAppInstanceId,
                userIdProvider.provideUserIdFuture(),
                advertiserIdProvider
        );
    }

    @NonNull
    AsyncFuture<String> getInstallInstanceIdInternal() {
        return attributionIdManager.getOrCreateInstallId();
    }
}
