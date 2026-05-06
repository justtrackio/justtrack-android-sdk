package io.justtrack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.play.core.integrity.StandardIntegrityManager;

import io.justtrack.attribution.Attribution;
import io.justtrack.deeplinks.DeepLinkListener;
import io.justtrack.exceptions.SdkNotTrackingException;
import io.justtrack.log.LoggerFields;
import io.justtrack.log.LoggerFieldsBuilder;
import io.justtrack.retargeting.PreliminaryRetargetingParameters;
import io.justtrack.retargeting.RetargetingParameters;
import io.justtrack.util.ExecutorServiceFactoryImpl;
import io.justtrack.versions.VersionBundle;

class JustTrackSdkImpl extends BaseJustTrackSdk {
    @Nullable
    private PreliminaryRetargetingParametersImpl preliminaryRetargetingParametersImpl;
    @NonNull
    private final ConnectivityProvider connectivityProvider;
    @NonNull
    private final AttributionOutputProvider attributionOutputProvider;
    @Nullable
    private Subscription reconnectSubscription;
    @Nullable
    private Intent intent;
    private boolean handleIntentsOnResume;

    JustTrackSdkImpl(@NonNull JustTrackSdkBuilder builder) {
        this(builder, new HttpClientBuilder() {
            @NonNull
            @Override
            public HttpClient build(
                    @NonNull Context context,
                    @NonNull String apiToken,
                    @NonNull Environment environment,
                    @NonNull PlatformType platformType
            ) {
                return new HttpClientImpl(
                        builder.getDeviceInfo(),
                        apiToken,
                        environment,
                        builder.getPackageName(),
                        new VersionBundle(builder.getSdkVersion(), builder.getApplicationVersion()),
                        builder.getLogger().getValue().getFallback()
                );
            }
        });
    }

    @VisibleForTesting
    @NonNull
    static JustTrackSdkImpl createForTesting(
            @NonNull JustTrackSdkBuilder builder,
            @NonNull HttpClient httpClient,
            @NonNull RetryConfig retryConfig,
            @Nullable ClaimProvider claimProvider,
            @Nullable StandardIntegrityManager.StandardIntegrityTokenProvider standardIntegrityTokenProvider
    ) {
        JustTrackSdkImpl sdk = new JustTrackSdkImpl(
                builder,
                (context, apiToken, environment, platformType) -> httpClient,
                retryConfig,
                claimProvider,
                builder.getStartConfigBuilder().build(),
                standardIntegrityTokenProvider
        );
        // need to call init because the instanceManager normally does this
        sdk.init(builder);
        // set the instance on the instance manager so other services can find the correct SDK
        InstanceManager.setInstance(sdk);
        // notify about the app start because the init provider no longer does this for a test
        JustTrack.notifyAppStart();

        sdk.start();

        return sdk;
    }

    JustTrackSdkImpl(@NonNull JustTrackSdkBuilder builder, HttpClientBuilder httpClientBuilder) {
        this(
                builder,
                httpClientBuilder,
                RetryConfig.Companion.getDEFAULT_CONFIG(),
                null,
                builder.getStartConfigBuilder().build(),
                null
        );
    }

    JustTrackSdkImpl(@NonNull JustTrackSdkBuilder builder,
                     @NonNull HttpClientBuilder httpClientBuilder,
                     @NonNull RetryConfig retryConfig,
                     @Nullable ClaimProvider claimProvider,
                     @NonNull JustTrackSdkConfig sdkConfig,
                     @Nullable StandardIntegrityManager.StandardIntegrityTokenProvider standardIntegrityTokenProvider
    ) {
        super(
                builder.getApplication(),
                builder.getApiToken(),
                builder.getPackageName(),
                builder.getApplicationVersion(),
                new ExecutorServiceFactoryImpl(),
                sdkConfig,
                builder.getLogger().getValue(),
                builder.isLogEnabled(),
                httpClientBuilder.build(
                        builder.getApplication(),
                        builder.getApiToken(),
                        builder.getEnvironment(),
                        builder.getSdkVersion().getPlatformType()
                ),
                retryConfig,
                builder.getEnvironment(),
                5_000L,
                SessionManagerImpl::new,
                builder.getRunCallbacksSerially(),
                DatabaseInterface.getInstance(builder.getApplication(), builder.getLogger().getValue()),
                claimProvider,
                builder.getSdkVersion(),
                standardIntegrityTokenProvider,
                builder.getIntegrationAdapters(),
                builder.getDeviceInfo()
        );
        handleIntentsOnResume = true;
        intent = builder.getIntent();
        if (intent == null) {
            Activity currentActivity = JustTrack.getCurrentActivity();
            if (currentActivity != null) {
                intent = currentActivity.getIntent();
            }
        }
        preliminaryRetargetingParametersImpl = PreliminaryRetargetingParametersImpl.fromIntent(intent);

        InstallReferrerProvider installReferrerProvider = context -> new InstallReferrerReader(
                builder.getApplication(),
                builder.getInstallReferrerDetailBundle(),
                logger
        );
        reconnectSubscription = null;
        this.attributionOutputProvider = new AttributionOutputProvider(
                this,
                deviceInfo,
                taskExecutor,
                databaseInterface,
                new AttributionOutputProvider.AttributionParams(
                        attributionIdManager,
                        new ChainedReAttributionDecider(
                                new ResolveOrganicAttributionDecider(),
                                new IntentLaunchDecider(preliminaryRetargetingParametersImpl),
                                builder.getReAttributionConfig()
                        ),
                        installReferrerProvider,
                        this.claimProvider,
                        retryConfig,
                        builder.getReAttributionConfig().getReFetchReAttributionDelaySeconds(),
                        builder.getAttributionRetryDelaySeconds(),
                        sdkConfig,
                        intent
                ),
                new AttributionOutputProvider.Loggers(logger, networkErrorLogger),
                new VersionBundle(builder.getSdkVersion(), builder.getApplicationVersion())
        );

        connectivityProvider = ConnectivityProvider.createProvider(builder.getApplication());

        applyingSDKConfig();
    }

    void init(@NonNull JustTrackSdkBuilder builder) {
        // we can only call Lifecycle#getCurrentState on the main thread
        new Handler(context.getMainLooper()).post(() -> {
            try {
                Lifecycle lc = ProcessLifecycleOwner.get().getLifecycle();
                if (lc.getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                    sessionManager.onResume();
                } else {
                    lc.addObserver(new LifecycleEventObserver() {
                        @Override
                        public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
                            if (event == Lifecycle.Event.ON_RESUME) {
                                lc.removeObserver(this);
                                sessionManager.onResume();
                            }
                        }
                    });
                }
            } catch (Throwable exception) {
                logger.warn("Failed to check lifecycle of process", exception);
            }
        });

        JustTrack.initWithSdk(builder.getApplication(), this);
        reconnectSubscription = connectivityProvider.registerOnReconnected(connected -> {
            if (connected) {
                this.onReconnect();
            }
        });
        publishEventsQueue.start(connectivityProvider);
        retrySendPersistId(PersistentIdStore.REASON_APP_START);
        integrationManager.integrateWithAdjoe();
    }

    @Override
    protected void handleNewIntent(@Nullable Intent newIntent, boolean isAutomatic) {
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
            getAttributionOutput(AttributionDecision.FETCH_RETARGETING_ATTRIBUTION);
        }
    }

    @NonNull
    @Override
    public Subscription registerDeepLinkListener(@NonNull DeepLinkListener deepLinkListener) {
        if (intent != null) {
            Uri deepLinkUri = intent.getData();
            if (deepLinkUri != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
                callbackInvoker.execute(
                        () -> performDeepLinkCall(new DeepLinkDataImpl(deepLinkUri), deepLinkListener),
                        exception -> logger.warn("Could not call deep link subscription, SDK is shutting down", exception)
                );
            }
        }

        return super.registerDeepLinkListener(deepLinkListener);
    }

    @Override
    public void shutdown() {
        InstanceManager.clearInstance(this);
        if (reconnectSubscription != null) {
            reconnectSubscription.unsubscribe();
            reconnectSubscription = null;
        }
        connectivityProvider.shutdown();
        super.shutdown();
    }

    @NonNull
    @Override
    public AsyncFuture<Attribution> getAttribution() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return new TransformingFuture<>(getAttributionOutput(null), AttributionOutput::getAttribution);
    }

    @NonNull
    protected AsyncFuture<AttributionResponse> getAttributionResponse() {
        return new TransformingFuture<>(getAttributionOutput(null), AttributionOutput::getAttributionResponse);
    }

    @VisibleForTesting
    protected AsyncFuture<AttributionOutput> getAttributionOutput(@Nullable AttributionDecision forcedDecision) {
        return attributionOutputProvider.provideAttributionOutput(
                forcedDecision,
                getAdvertiserIdInfoInternal(),
                getAppSetId(),
                trackingId,
                trackingProvider,
                getIntegritySecret()
        );
    }

    void runAttributionTask(
            @NonNull AsyncFuture<AttributionOutput> attributionFuture,
            @Nullable AttributionResponse storedResponse,
            @Nullable AttributionTimestamps attributionTimestamps,
            @Nullable AttributionDecision forcedDecision,
            @NonNull AttributionDecision attributionDecision
    ) {
        try {
            AttributionOutput output = attributionFuture.get();
            onAttributionDone(output);
            if (attributionTimestamps == null) {
                logger.debug("Fetched first attribution");
            } else if (!attributionDecision.shouldFetchAttribution()) {
                logger.info("Attribution was not needed, but was not cached");
            } else {
                long attributionAge = attributionTimestamps.getLastAttributionAt() - attributionTimestamps.getFirstAttributionAt();
                long now = System.currentTimeMillis();
                long sinceLastOpen = now - attributionTimestamps.getLastOpenAt();
                long sinceLastAttribution = now - attributionTimestamps.getLastAttributionAt();
                LoggerFields fields = new LoggerFieldsBuilder()
                        .with("attributionAge", attributionAge)
                        .with("sinceLastOpen", sinceLastOpen)
                        .with("sinceLastAttribution", sinceLastAttribution)
                        .with("force", forcedDecision != null);
                logger.debug("Attribution was fetched again because re-attribution was needed", fields);
            }
            callAttributionSubscriptions(output.getAttributionResponse());
            callRetargetingParametersSubscriptions(output.getRetargetingParameters());

            boolean needsClaimsRefetch = output.getAttributionResponse().getCampaign().isOrganic()
                    && output.didClaimsTimeOut()
                    && attributionDecision.isFastClaimsTimeout();
            boolean needsReAttributionRefetch = attributionDecision.isFetchRetargetingAttribution()
                    && (storedResponse == null || output.getAttributionResponse().getInstallId().equals(storedResponse.getInstallId()))
                    && attributionOutputProvider.getReFetchReAttributionDelaySeconds() > 0;

            // check if we expected a re-attribution and did not get one
            if (needsReAttributionRefetch) {
                String fetchWithNeedClaimRefetch = "Fetching attribution again (with longer claims timeout) "
                        + "because the retargeting delay expired and previously the attribution did not change";

                String fetchWithoutNeedClaimRefetch = "Fetching attribution again because the retargeting delay "
                        + "expired and previously the attribution did not change";
                fetchAttributionAgainAfter(
                        needsClaimsRefetch ? fetchWithNeedClaimRefetch : fetchWithoutNeedClaimRefetch,
                        needsClaimsRefetch
                                ? AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED.withSlowClaimTimeout()
                                : AttributionDecision.FETCH_RETARGETING_ATTRIBUTION_DELAYED,
                        attributionOutputProvider.getReFetchReAttributionDelaySeconds()
                );
            } else {
                final PreliminaryRetargetingParametersImpl localPreliminaryRetargetingParametersImpl;
                // Deadlock-Safety: We only read a field and store it in a local variable.
                synchronized (this) {
                    localPreliminaryRetargetingParametersImpl = preliminaryRetargetingParametersImpl;
                }
                if (localPreliminaryRetargetingParametersImpl != null) {
                    localPreliminaryRetargetingParametersImpl.resolve(output);
                }
                // trigger fetching the attribution again (with the claims this time, if possible)
                if (needsClaimsRefetch) {
                    fetchAttributionAgainAfter(
                            "Fetching attribution again with longer timeout while waiting for claims",
                            AttributionDecision.FETCH_FIRST_ATTRIBUTION.withSlowClaimTimeout(),
                            3 // we don't really need to wait for a long time before trying again
                    );
                }
            }
        } catch (Throwable exception) {
            networkErrorLogger.logException(
                    logger,
                    exception,
                    "Failed to wait for attribution"
            );
            // Deadlock-Safety: We only update local state, classify errors, or call simple functions
            synchronized (this) {
                // if we still have the same value as before, replace it by an error future so we know we can retry getting the attribution
                if (attributionOutputProvider.getLatestApiAttributionOutput() == attributionFuture) {
                    attributionOutputProvider.setOutput(new ErrorFuture<>(exception));
                    if (AttributionErrorClassifier.getInstance().unrecoverable(exception)) {
                        // use an hour as "infinity" - if the app is still running after 1h, we can take the hit, if not, we don't leave the chance
                        // that something lingers in memory and the next time we run we can't handle this
                        attributionOutputProvider.setAttributionCanRetryAt(System.currentTimeMillis() + 3600 * 1000);
                    } else {
                        // retry only after at least the wait time is over
                        long waitTime = (long) AttributionErrorClassifier.getInstance().waitTime(exception);
                        attributionOutputProvider.setAttributionCanRetryAt(
                                System.currentTimeMillis() + Math.max(attributionOutputProvider.getAttributionRetryDelaySeconds() * 1000, waitTime)
                        );
                    }
                }
            }
        }
    }

    private void onAttributionDone(@NonNull AttributionOutput output) {
        String installId = output.getAttributionResponse().getInstallId();
        checkInstallIdChange(installId);
    }

    @VisibleForTesting
    void checkInstallIdChange(String installId) {
        attributionIdManager.checkInstallIdChange(
                installId,
                getUserId(),
                getAdvertiserIdInfoInternal()
        );
    }

    private void fetchAttributionAgainAfter(@NonNull String message, @NonNull AttributionDecision forcedDecision, long delaySeconds) {
        Handler handler = new Handler(context.getMainLooper());
        handler.postDelayed(() -> {
            logger.info(message);
            getAttributionOutput(forcedDecision);
        }, delaySeconds * 1000);
    }

    private void onReconnect() {
        retryAttributionAfterReconnect();
        retrySendPersistId(PersistentIdStore.REASON_RECONNECT);
    }

    private void retryAttributionAfterReconnect() {
        boolean needToRefetch = false;
        // Deadlock-Safety: We only read and write some fields.
        synchronized (this) {
            if (attributionOutputProvider.getOutput() instanceof ErrorFuture) {
                attributionOutputProvider.setOutput(null);
                attributionOutputProvider.setAttributionCanRetryAt(0L);
                needToRefetch = true;
            }
        }

        if (needToRefetch) {
            logger.info("Fetching attribution again as it failed and we got a new network connection");
            // the attribution failed and can be retried
            getAttributionOutput(AttributionDecision.FETCH_FIRST_ATTRIBUTION);
        }
    }

    private void retrySendPersistId(@NonNull String reason) {
        @Nullable String pendingCustomUserId = CustomUserIdStore.getInstance().getPendingId(context);
        if (pendingCustomUserId != null) {
            customIdManager.sendCustomUserId(
                    pendingCustomUserId,
                    getUserId(),
                    attributionIdManager,
                     getAdvertiserIdInfoInternal(),
                    reason
            );
        }

        @Nullable String pendingFirebaseId = FirebaseIdStore.getInstance().getPendingId(context);
        if (pendingFirebaseId != null) {
            firebaseIdManager.sendFirebaseId(
                    attributionIdManager,
                    new FirebaseIdManager.AttributionParams(
                           getUserId(),
                            getAdvertiserIdInfoInternal(),
                            pendingFirebaseId
                    ),
                    reason
            );
        }
    }

    private void applyingSDKConfig() {
        new SdkConfigTask(taskExecutor, databaseInterface, logger, httpClient);
    }

    @NonNull
    @Override
    public AsyncFuture<RetargetingParameters> getRetargetingParameters() {
        if (!isTracking.get()) {
            return new ErrorFuture<>(new SdkNotTrackingException());
        }

        return new TransformingFuture<>(getAttributionOutput(null), AttributionOutput::getRetargetingParameters);
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
}
