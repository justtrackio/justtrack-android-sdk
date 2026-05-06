package io.justtrack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.appset.AppSetIdInfo;
import com.google.android.play.core.integrity.StandardIntegrityManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.justtrack.ads.AdImpression;
import io.justtrack.ads.AdImpressionState;
import io.justtrack.attribution.AdvertiserIdInfo;
import io.justtrack.config.RemoteConfig;
import io.justtrack.config.RemoteConfigImpl;
import io.justtrack.crashes.CrashHandler;
import io.justtrack.crashes.CrashReportingFactory;
import io.justtrack.crashes.CrashReportingFactoryImpl;
import io.justtrack.crashes.CrashType;
import io.justtrack.deeplinks.DeepLinkData;
import io.justtrack.deeplinks.DeepLinkListener;
import io.justtrack.events.JtAdInternalEvent;
import io.justtrack.events.JtDeeplinkHandledEvent;
import io.justtrack.events.JtDeeplinkNotHandledEvent;
import io.justtrack.events.JtPurchaseInternalEvent;
import io.justtrack.events.Money;
import io.justtrack.exceptions.InvalidFieldException;
import io.justtrack.exceptions.SdkNotTrackingException;
import io.justtrack.integrations.IntegrationAdapter;
import io.justtrack.log.Logger;
import io.justtrack.log.LoggerFieldsBuilder;
import io.justtrack.retargeting.PreliminaryRetargetingParameters;
import io.justtrack.retargeting.PreliminaryRetargetingParametersListener;
import io.justtrack.retargeting.RetargetingParameters;
import io.justtrack.retargeting.RetargetingParametersListener;
import io.justtrack.util.ExecutorServiceFactory;
import io.justtrack.versions.SdkVersion;
import io.justtrack.versions.VersionBundle;

abstract class BaseJustTrackSdk implements JustTrackSdk {
    @NonNull
    private final PeriodicLogsPublisher periodicLogsPublisher;
    @NonNull
    protected final Context context;
    @NonNull
    private final ExecutorService executor;
    @NonNull
    protected final HttpClient httpClient;
    @NonNull
    protected final RetryConfig retryConfig;
    @NonNull
    protected final ClaimProvider claimProvider;
    @NonNull
    protected final PublishEventsQueue publishEventsQueue;
    @NonNull
    protected final AppVersionProvider appVersionProvider;
    @NonNull
    private final AdvertiserIdReader advertiserIdProvider;
    @NonNull
    private final UserIdProvider userIdProvider;
    @NonNull
    private final AppSetIdProvider appSetIdProvider;
    @NonNull
    protected final TestGroupIdProvider testGroupIdProvider;
    @NonNull
    protected final IntegrityTokenProvider integrityTokenProvider;
    @NonNull
    protected final IntegritySecretProvider integritySecretProvider;
    @NonNull
    protected final RemoteConfig remoteConfig;
    @NonNull
    private final SubscriptionManager<AttributionListener> attributionSubscriptions;
    @NonNull
    private final SubscriptionManager<RetargetingParametersListener> retargetingParametersSubscriptions;
    @NonNull
    private final SubscriptionManager<PreliminaryRetargetingParametersListener> preliminaryRetargetingParametersSubscriptions;
    @NonNull
    private final SubscriptionManager<DeepLinkListener> deepLinkSubscriptions;
    @NonNull
    protected final IntegrationManager integrationManager;
    @NonNull
    protected final SessionManager sessionManager;
    @NonNull
    final HttpLogger logger;
    @Nullable
    private final BillingTracker billingTracker;
    @NonNull
    protected final CallbackInvoker callbackInvoker;
    @NonNull
    protected final JtCrashReporter crashReporter;
    @NonNull
    protected final CrashHandler crashHandler;
    @NonNull
    protected final DatabaseInterface databaseInterface;
    @NonNull
    protected final IntegrityTokenPublisher integrityTokenPublisher;
    @NonNull
    protected final AttributionIdManager attributionIdManager;
    @NonNull
    protected final SdkConfigDelegate sdkConfigDelegate;
    @NonNull
    protected final NetworkErrorLogger networkErrorLogger;
    @NonNull
    protected final AtomicBoolean isTracking = new AtomicBoolean(false);
    @Nullable
    protected String trackingId = null;
    @NonNull
    protected String trackingProvider = "advertiserId";
    protected boolean started = false;
    @NonNull
    protected final JustTrackSdkConfig startConfig;
    @NonNull
    protected final TaskExecutor taskExecutor;
    @NonNull
    private final List<IntegrationAdapter> pendingIntegrationAdapters = new ArrayList<>();
    @NonNull
    private final VersionBundle versionBundle;
    private final List<IntegrationAdapter> integratedAdapters = new ArrayList<>();
    @NonNull
    protected final DeviceInfo deviceInfo;
    @NonNull
    protected final FirebaseIdManager firebaseIdManager;
    @NonNull
    protected final CustomIdManager customIdManager;
    @NonNull
    protected final SdkFirstInitializationTimestampRepo sdkFirstInitializationTimestampRepo;

    protected BaseJustTrackSdk(
            @NonNull Context context,
            @NonNull String apiToken,
            @NonNull String applicationPackageName,
            @NonNull ApplicationVersion applicationVersion,
            @NonNull ExecutorServiceFactory executorFactory,
            @NonNull JustTrackSdkConfig startConfig,
            @NonNull Logger fallback,
            boolean isLogEnabled,
            @NonNull HttpClient httpClient,
            @NonNull RetryConfig retryConfig,
            @NonNull Environment environment,
            long eventsWaitTimeMS,
            @NonNull SessionManagerBuilder sessionManagerBuilder,
            boolean runCallbacksSerially,
            @NonNull DatabaseInterface databaseInterface,
            @Nullable ClaimProvider claimProvider,
            @NonNull SdkVersion sdkVersion,
            @Nullable StandardIntegrityManager.StandardIntegrityTokenProvider standardIntegrityTokenProvider,
            @NonNull List<IntegrationAdapter> integrationAdapters,
            @NonNull DeviceInfo deviceInfo
    ) {
        this.context = context;
        this.databaseInterface = databaseInterface;
        this.networkErrorLogger = new NetworkErrorLogger();
        this.startConfig = startConfig;
        this.versionBundle = new VersionBundle(sdkVersion, applicationVersion);
        LogAggregatorImpl logAggregator = new LogAggregatorImpl(
                new MessageRepositoryImpl(Formatter.INSTANCE, this.databaseInterface.openMessages(), fallback),
                new MetricRepositoryImpl(Formatter.INSTANCE, this.databaseInterface.openMetrics(), fallback),
                fallback,
                networkErrorLogger,
                isTracking
        );
        this.logger = new HttpLoggerImpl(
                fallback,
                httpClient,
                versionBundle,
                logAggregator
        );

        databaseInterface.setLogger(logger);

        this.deviceInfo = deviceInfo;
        this.crashReporter = new JtCrashReporter(context, logger, Formatter.INSTANCE, isTracking);
        final CrashReportingFactory crashReportingFactory =
                new CrashReportingFactoryImpl(crashReporter);
        this.crashHandler = new CrashHandler(applicationPackageName, crashReportingFactory.create(), logger, isTracking);

        this.logger.setBreadCrumbReporter(crashReporter);
        this.executor = executorFactory.create(this.crashHandler);
        this.callbackInvoker = runCallbacksSerially ? new SerializedCallbackInvoker() : new DefaultCallbackInvoker();

        this.taskExecutor = new TaskExecutorImpl(this, executor, callbackInvoker);

        this.firebaseIdManager = new FirebaseIdManager(
                context,
                taskExecutor,
                httpClient,
                deviceInfo,
                logger,
                networkErrorLogger
        );
        this.customIdManager = new CustomIdManager(
                context,
                taskExecutor,
                deviceInfo,
                httpClient,
                logger,
                networkErrorLogger
        );

        this.attributionIdManager = new AttributionIdManager(
                taskExecutor,
                context,
                databaseInterface,
                customIdManager,
                firebaseIdManager
        );

        this.httpClient = httpClient;
        this.retryConfig = retryConfig;

        this.periodicLogsPublisher = new PeriodicLogsPublisher(this.logger);
        if (claimProvider != null) {
            this.claimProvider = claimProvider;
        } else {
            this.claimProvider = new ClaimProviderImpl(logger);
        }
        this.publishEventsQueue = new PublishEventsQueue(
                this::runPublishEventTask,
                logger,
                networkErrorLogger,
                new EventRepositoryImpl(
                        Formatter.INSTANCE,
                        versionBundle.getSdkVersion().getPlatformType(),
                        this.databaseInterface.openEvents(),
                        logger
                ),
                eventsWaitTimeMS,
                isTracking,
                versionBundle.getSdkVersion()
        );
        this.appVersionProvider = new AppVersionProvider(taskExecutor, applicationVersion, databaseInterface);
        this.advertiserIdProvider = new AdvertiserIdReader();
        this.userIdProvider = new UserIdProvider(taskExecutor, deviceInfo, applicationPackageName);
        this.appSetIdProvider = new AppSetIdProvider();
        this.testGroupIdProvider = new TestGroupIdProvider(taskExecutor);
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
        this.sdkFirstInitializationTimestampRepo = new SdkFirstInitializationTimestampRepo(
                context.getSharedPreferences(
                        SdkFirstInitializationTimestampRepo.STORE_NAME,
                        Context.MODE_PRIVATE
                )
        );
        this.remoteConfig = new RemoteConfigImpl(
                context,
                isTracking,
                new RemoteConfigImpl.AttributionParams(
                        this::getInstallInstanceIdInternal,
                        this::getUserId,
                        this::getAdvertiserIdInfoInternal,
                        databaseInterface,
                        deviceInfo,
                        sdkVersion
                ),
                sdkFirstInitializationTimestampRepo,
                taskExecutor,
                httpClient,
                logger
        );
        this.attributionSubscriptions = new SubscriptionManager<>();
        this.retargetingParametersSubscriptions = new SubscriptionManager<>();
        this.preliminaryRetargetingParametersSubscriptions = new SubscriptionManager<>();
        this.deepLinkSubscriptions = new SubscriptionManager<>();
        this.sessionManager = sessionManagerBuilder.build(this, context, isTracking);
        this.integrationManager = new IntegrationManager(this, logger);
        this.billingTracker = BillingTrackerFactory.getBillingClient(this, context, logger);
        synchronized (pendingIntegrationAdapters) {
            this.pendingIntegrationAdapters.addAll(integrationAdapters);
        }

        this.sdkConfigDelegate = new SdkConfigDelegate(firebaseIdManager, attributionIdManager);
    }

    @Override
    public void start() {
        start(startConfig);
    }

    protected void start(@NonNull JustTrackSdkConfig config) {
        isTracking.set(true);

        sdkFirstInitializationTimestampRepo.getOrCreate();

        logger.setUser(
                attributionIdManager.getUserId(),
                getInstallInstanceIdInternal()
        );

        if (config.userId != null) {
            setUserIdInternal(config.userId);
        }

        this.trackingProvider = config.trackingIdProvider;
        if (config.trackingId != null) {
            this.trackingId = config.trackingId;
        }

        setAutomaticInAppPurchaseTracking(config.automaticIAPTracking);
        sdkConfigDelegate.applyingConfig(config, getUserId(), getAdvertiserIdInfoInternal());

        // only happen for the first time.
        if (!started) {
            started = true;
            this.sessionManager.start(this);
            JustTrack.notifyQueuedEvents();

            publishIntegrityToken();
        }

        synchronized (pendingIntegrationAdapters) {
            for (IntegrationAdapter pendingAdapter : pendingIntegrationAdapters) {
                pendingAdapter.integrate(context, this, logger);
                integratedAdapters.add(pendingAdapter);
            }

            pendingIntegrationAdapters.clear();
        }

        getAttributionResponse();

        crashReporter.report();
    }

    @Override
    public boolean isRunning() {
        return isTracking.get();
    }

    @Override
    public void stop() {
        isTracking.set(false);
    }

    @Override
    public AsyncFuture<Boolean> anonymize() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return new AnonymizeUserHandler(
                taskExecutor,
                deviceInfo,
                httpClient,
                logger,
                getUserId(),
                getInstallInstanceIdInternal(),
                getAdvertiserIdInfoInternal()
        ).anonymizeUser();
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
    public AsyncFuture<Void> setExperimentVariant(String experiment, String variant, @Nullable Date happenedAt) {
        return setExperimentVariant(experiment, variant, new ArrayList<>(), happenedAt);
    }

    @NonNull
    @Override
    public AsyncFuture<Void> setExperimentVariant(
            String experiment,
            String variant,
            List<String> tags,
            @Nullable Date happenedAt
    ) {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        if (!Validation.validCommonInput(experiment, 256)) {
            InvalidFieldException exception = new InvalidFieldException("experiment", experiment, 256, "ASCII");
            logger.warn("Unable to set experiment variant with invalid experiment", exception);

            return new ErrorFuture<>(exception);
        }

        if (!Validation.validCommonInput(variant, 256)) {
            InvalidFieldException exception = new InvalidFieldException("variant", variant, 256, "ASCII");
            logger.warn("Unable to set experiment variant with invalid variant", exception);

            return new ErrorFuture<>(exception);
        }

        int tagLimit = 5;
        if (tags != null) {
            if (tags.size() > tagLimit) {
                InvalidFieldException exception = new InvalidFieldException("Too many tags, the number of tags must not exceed %d", tagLimit);
                logger.warn("Unable to set experiment variant with an invalid number of tags: " + tags.size());

                return new ErrorFuture<>(exception);

            }

            for (String tag : tags) {
                if (!Validation.validCommonInput(tag, 64)) {
                    InvalidFieldException exception = new InvalidFieldException("tags", tag, 64, "ASCII");
                    logger.warn("Unable to set experiment variant with invalid tags");

                    return new ErrorFuture<>(exception);
                }
            }
        }
        SetExperimentVariantHandler setExperimentVariantHandler = new SetExperimentVariantHandler(
                taskExecutor,
                deviceInfo,
                httpClient,
                logger
        );
        return setExperimentVariantHandler.setExperimentVariant(
                new SetExperimentVariantHandler.AttributionParams(
                        getInstallInstanceIdInternal(),
                        getUserId(),
                        getAdvertiserIdInfoInternal()
                ),
                new SetExperimentVariantHandler.ExperimentParams(
                        experiment,
                        variant,
                        tags
                ),
                getSdkVersion(),
                appVersionProvider.getCurrentVersion(),
                happenedAt
        );
    }

    interface SessionManagerBuilder {
        @NonNull
        SessionManager build(@NonNull BaseJustTrackSdk sdk, @NonNull Context context, @NonNull AtomicBoolean isTracking);
    }

    @NonNull
    AsyncFuture<List<PublishingEvent>> runPublishEventTask(@NonNull List<PublishingEvent> events, @NonNull SdkVersion sdkVersion) {
        Task<List<PublishingEvent>> publishEventsTask = new PublishEventsTask<>(
                deviceInfo,
                logger,
                events,
                new PublishEventsTask.AttributionParams(
                        getAdvertiserIdInfoInternal(),
                        // wait for our attribution instead of using the user id directly - the backend verifies
                        // that the install event exists, so we need to have an attribution before sending any
                        // user events
                        new TransformingFuture<>(getAttributionResponse(), AttributionResponse::getUserId),
                        trackingId,
                        trackingProvider,
                        this::getInstallInstanceIdInternal
                ),
                new VersionBundle(sdkVersion, versionBundle.getApplicationVersion()),
                httpClient
        );
        Task<List<PublishingEvent>> retryingTask = new RetryingTask<>(
                publishEventsTask,
                deviceInfo,
                logger,
                retryConfig.getPublishEventsRetries(),
                TrackingEventErrorClassifier.getInstance(),
                HttpClientImpl.SEND_USER_EVENTS_REQUEST_NAME
        );

        return taskExecutor.executeAsFuture(retryingTask);
    }

    // Deadlock-Safety: This must not take any new locks, it is called with a lock already taken
    @NonNull
    AsyncFuture<String> spawnFetchClaimTask(@NonNull IPProtocol protocol) {
        FetchIpClaimTask task = new FetchIpClaimTask(deviceInfo, getAdvertiserIdInfoInternal(), httpClient, logger, protocol);
        Task<String> retryingTask = new RetryingTask<>(
                task,
                deviceInfo,
                logger,
                retryConfig.getFetchClaimRetries(),
                FetchClaimErrorClassifier.getInstance(),
                protocol.getRequestName()
        );

        return taskExecutor.executeAsFuture(retryingTask);
    }

    @Override
    @NonNull
    public AsyncFuture<AdvertiserIdInfo> getAdvertiserIdInfo() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return getAdvertiserIdInfoInternal();
    }

    protected AsyncFuture<AdvertiserIdInfo> getAdvertiserIdInfoInternal() {
        return advertiserIdProvider.provideAdvertiserIdFuture(taskExecutor, context, logger);
    }

    @NonNull
    AsyncFuture<String> getUserId() {
        return new TransformingFuture<>(getUserUUID(), UUID::toString);
    }

    @NonNull
    AsyncFuture<UUID> getUserUUID() {
        return userIdProvider.provideUserIdFuture(
                attributionIdManager,
                logger,
                getAdvertiserIdInfoInternal(),
                trackingId
        );
    }

    @NonNull
    protected AsyncFuture<AppSetIdInfo> getAppSetId() {
        return appSetIdProvider.provideAppSetIdFuture(taskExecutor, context, logger);
    }

    @NonNull
    protected AsyncFuture<IntegrityTokenData> getIntegrityToken() {
        return integrityTokenProvider.getOrRenewFuture(
                logger,
                getInstallInstanceIdInternal(),
                getIntegritySecret(),
                databaseInterface,
                null
        );
    }

    @NonNull
    protected AsyncFuture<String> getIntegritySecret() {
        return integritySecretProvider.provideIntegritySecret(getInstallInstanceIdInternal());
    }

    protected void callAttributionSubscriptions(@NonNull AttributionResponse storedResponse) {
        attributionSubscriptions.call(subscription -> callbackInvoker.execute(
                () -> subscription.onAttributionReceived(new AttributionImpl(storedResponse)),
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not call attribution subscription, SDK is shutting"
                    );
                }
        ));
    }

    protected void callRetargetingParametersSubscriptions(@Nullable RetargetingParameters retargetingParameters) {
        if (retargetingParameters == null) {
            return;
        }
        retargetingParametersSubscriptions.call(subscription -> callbackInvoker.execute(
                () -> subscription.onRetargetingParametersReceived(retargetingParameters),
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not call retargeting parameter subscription, SDK is shutting down"
                    );
                }
        ));
    }

    protected void callPreliminaryRetargetingParametersSubscriptions(
            @NonNull PreliminaryRetargetingParameters preliminaryRetargetingParameters) {
        preliminaryRetargetingParametersSubscriptions.call(subscription -> callbackInvoker.execute(
                () -> subscription.onPreliminaryRetargetingParametersReceived(preliminaryRetargetingParameters),
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not call preliminary retargeting parameter subscription, SDK is shutting down"
                    );
                }
        ));
    }

    protected void callDeepLinkSubscriptions(@NonNull DeepLinkData deepLink) {
        deepLinkSubscriptions.call(subscription -> callbackInvoker.execute(
                () -> performDeepLinkCall(deepLink, subscription),
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not call deep link subscription, SDK is shutting down"
                    );
                }
        ));
    }

    protected void performDeepLinkCall(@NonNull DeepLinkData deepLink, @NonNull DeepLinkListener subscription) {
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

    protected void reportReactNativeCrash(String message, String stackTrace) {
        reportWrapperCrash(CrashType.WRAPPER_CRASH.getId(), message, stackTrace, new Date());
    }

    protected void reportUnityCrash(String message, String stackTrace) {
        reportWrapperCrash(CrashType.WRAPPER_CRASH.getId(), message, stackTrace, new Date());
    }

    private void reportWrapperCrash(Integer crashType, String message, String stackTrace, Date date) {
        crashReporter.storeUncaughtException(crashType, message, stackTrace, date);
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
    public Subscription registerDeepLinkListener(@NonNull DeepLinkListener deepLinkListener) {
        return deepLinkSubscriptions.subscribe(deepLinkListener);
    }

    protected void execute(@NonNull Runnable task, @NonNull RejectedExecutionExceptionHandler rejectedHandler) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            rejectedHandler.handleRejectedExecution(exception);
        }
    }

    @Override
    public void onNewIntent(@Nullable Intent newIntent) {
        execute(
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

    protected abstract void handleNewIntent(@Nullable Intent newIntent, boolean isAutomatic);

    void onResume(@NonNull Activity activity) {
        Intent intent = activity.getIntent();
        execute(
                () -> {
                    handleNewIntent(intent, true);
                    sessionManager.onResume();
                    crashHandler.onResume();
                },
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not handle app resume, SDK is shutting down"
                    );
                }
        );
        periodicLogsPublisher.start();
    }

    void onPause() {
        sessionManager.onPause();
        periodicLogsPublisher.pause();
        crashHandler.onPause();
    }

    @Override
    public void shutdown() {
        try {
            integrationManager.close();
            sessionManager.shutdown(this);
            publishEventsQueue.close();
            logger.close();
        } catch (Exception exception) {
            networkErrorLogger.logException(
                    logger,
                    exception,
                    "Failed to close resources"
            );
        }
        periodicLogsPublisher.stop();
        execute(
                executor::shutdown,
                exception -> {
                    networkErrorLogger.logException(
                            logger,
                            exception,
                            "Could not shut down SDK, a shutdown is already in progress"
                    );
                }
        );
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.getFallback().warn("Timed out while waiting for executor termination");
                if (BuildConfig.DEBUG) {
                    for (Map.Entry<Thread, StackTraceElement[]> threadWithTrace : Thread.getAllStackTraces().entrySet()) {
                        Thread thread = threadWithTrace.getKey();
                        StringBuilder sb = new StringBuilder();
                        sb.append("Thread ")
                                .append(thread.getName())
                                .append(" (TID ")
                                .append(thread.getId())
                                .append(") in state ")
                                .append(thread.getState());

                        for (StackTraceElement element : threadWithTrace.getValue()) {
                            sb.append("\n  at ").append(element.toString());
                        }
                        logger.getFallback().warn(sb.toString());
                    }
                }
            }
        } catch (InterruptedException exception) {
            logger.getFallback().warn("Waiting for executor termination was interrupted", exception);
        }
    }

    @NonNull
    @Override
    public AsyncFuture<Void> publishEvent(@NonNull AppEvent event) {
        return publishEventsQueue.publishEvent(event, sessionManager);
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

    @NonNull
    protected AsyncFuture<Boolean> setUserIdInternal(@NonNull String userId) {
        return customIdManager.setCustomUserId(
                userId,
                getUserId(),
                attributionIdManager,
                getAdvertiserIdInfoInternal()
        );
    }

    @VisibleForTesting
    AsyncFuture<Boolean> publishIntegrityToken() {
        return integrityTokenPublisher.publishIntegrityTokenIfNotAlreadyRunning(
                logger,
                networkErrorLogger,
                httpClient,
                databaseInterface,
                this::getIntegrityToken,
                getInstallInstanceIdInternal()
        );
    }

    @Override
    @NonNull
    public AsyncFuture<Integer> getTestGroupId() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return getTestGroupIdInternal();
    }

    @NonNull
    protected AsyncFuture<Integer> getTestGroupIdInternal() {
        return testGroupIdProvider.provideTestGroupIdFuture(logger, getAdvertiserIdInfoInternal(), databaseInterface);
    }

    @NonNull
    TestGroupIdProvider getTestGroupIdProvider() {
        return testGroupIdProvider;
    }

    @Override
    @NonNull
    public AsyncFuture<Boolean> setFirebaseAppInstanceId(@NonNull String firebaseAppInstanceId) {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return setFirebaseAppInstanceIdInternal(firebaseAppInstanceId);
    }

    @NonNull
    protected AsyncFuture<Boolean> setFirebaseAppInstanceIdInternal(@NonNull String firebaseAppInstanceId) {
        return sdkConfigDelegate.setFirebaseAppInstanceId(
                firebaseAppInstanceId,
                getUserId(),
                getAdvertiserIdInfoInternal()
        );
    }

    @Override
    @NonNull
    public AsyncFuture<Void> forwardAdImpression(@NonNull AdImpression adImpression) {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        if (adImpression.getRevenue() != null && adImpression.getRevenue().getValue() < 0) {
            InvalidFieldException exception = new InvalidFieldException("Negative revenue for AdFormat");
            logger.warn(
                    "Negative revenue for AdFormat",
                    new LoggerFieldsBuilder()
                            .with("adUnit", adImpression.getUnit())
                            .with("revenue", adImpression.getRevenue().getValue())
                            .with("currency", adImpression.getRevenue().getCurrency()));
            return new ErrorFuture<>(exception);
        }

        if (adImpression.getUnit().isEmpty()) {
            InvalidFieldException exception = new InvalidFieldException("Empty AdUnit");

            logger.warn("Empty AdUnit");

            return new ErrorFuture<>(exception);
        }

        AppEvent event = new JtAdInternalEvent(
                "success",
                adImpression.getBundleId() != null ? adImpression.getBundleId() : "",
                adImpression.getInstanceName() != null ? adImpression.getInstanceName() : "",
                adImpression.getNetwork() != null ? adImpression.getNetwork() : "",
                adImpression.getPlacement() != null ? adImpression.getPlacement() : "",
                adImpression.getSdkName(),
                adImpression.getSegmentName() != null ? adImpression.getSegmentName() : "",
                adImpression.getUnit(),
                adImpression.getTestGroup() != null ? adImpression.getTestGroup() : "",
                adImpression.getRevenue(),
                new Date()
        );


        final AdImpressionState impressionState = adImpression.getState();
        if (impressionState != null) {
            event.addDimension("jt_impression_state", impressionState
                    .getEncodedName());
        }

        if (adImpression.getRevenue() != null) {
            event.setValue(adImpression.getRevenue());
        } else {
            event.setValue(new Money(0, "USD"));
        }

        try {
            event.validate();
        } catch (InvalidFieldException exception) {
            logger.warn(
                    "Not publishing invalid ad impression",
                    new LoggerFieldsBuilder()
                            .with("adUnit", adImpression.getUnit())
                            .with("exception", exception)
            );

            return new ErrorFuture<>(exception);
        }

        return publishEvent(event);
    }

    @Override
    public boolean forwardInApp(@NonNull String productId, @NonNull String token, @NonNull Money totalPrice) {
        if (totalPrice.getValue() < 0) {
            logger.warn(
                    "Negative revenue for product purchase",
                    new LoggerFieldsBuilder()
                            .with("productId", productId)
                            .with("revenue", totalPrice.getValue())
                            .with("currency", totalPrice.getCurrency())
            );

            return false;
        }

        try {
            totalPrice.validate();
        } catch (InvalidFieldException exception) {
            logger.warn(
                    "Not publishing invalid product purchase",
                    new LoggerFieldsBuilder()
                            .with("productId", productId)
                            .with("exception", exception)
            );

            return false;
        }

        publishEvent(new JtPurchaseInternalEvent("success", productId, token, "purchase", totalPrice, new Date()));

        return true;
    }

    @Override
    public boolean forwardSubscription(@NonNull String subscriptionId, @NonNull String token, @NonNull Money subscriptionPrice) {
        if (subscriptionPrice.getValue() < 0) {
            logger.warn(
                    "Negative revenue for subscription purchase",
                    new LoggerFieldsBuilder()
                            .with("subscriptionId", subscriptionId)
                            .with("revenue", subscriptionPrice.getValue())
                            .with("currency", subscriptionPrice.getCurrency())
            );

            return false;
        }

        try {
            subscriptionPrice.validate();
        } catch (InvalidFieldException exception) {
            logger.warn(
                    "Not publishing invalid subscription purchase",
                    new LoggerFieldsBuilder()
                            .with("subscriptionId", subscriptionId)
                            .with("exception", exception)
            );

            return false;
        }

        publishEvent(new JtPurchaseInternalEvent("success", subscriptionId, token, "subscription", subscriptionPrice, new Date()));

        return true;
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

    void notifyAppStart(@NonNull AppStartDuration startEvent) {
        new NotifyAppStartHandler(
                this,
                taskExecutor,
                context,
                sessionManager,
                startEvent,
                appVersionProvider.providAppVersionUpdateInfo(),
                logger
        ).notifyAppStart();
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

    @NonNull
    protected AsyncFuture<String> getInstallInstanceIdInternal() {
        return attributionIdManager.getOrCreateInstallId();
    }

    @NonNull
    protected abstract AsyncFuture<AttributionResponse> getAttributionResponse();

    @Override
    public void setAutomaticInAppPurchaseTracking(boolean enabled) {
        if (billingTracker != null) {
            billingTracker.setEnable(enabled);
        }
    }

    private class DefaultCallbackInvoker implements CallbackInvoker {
        @Override
        public void invoke(@NonNull Runnable callback) {
            callback.run();
        }

        @Override
        public void execute(@NonNull Runnable task, @NonNull RejectedExecutionExceptionHandler rejectedHandler) {
            BaseJustTrackSdk.this.execute(task, rejectedHandler);
        }

        @NonNull
        @Override
        public <V> Callback<V> wrap(@NonNull Callback<V> callback) {
            return callback;
        }
    }

    interface PublishEventsTaskRunner {
        @NonNull
        AsyncFuture<List<PublishingEvent>> runPublishEventTask(@NonNull List<PublishingEvent> events, @NonNull SdkVersion sdkVersion);
    }

}
