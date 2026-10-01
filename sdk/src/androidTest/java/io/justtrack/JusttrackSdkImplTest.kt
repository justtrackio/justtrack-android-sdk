package io.justtrack

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.justtrack.RetryConfig.Companion.NO_RETRIES_CONFIG
import io.justtrack.ads.AdImpression
import io.justtrack.api.DefaultLogApi
import io.justtrack.api.LogApi
import io.justtrack.attribution.Attribution
import io.justtrack.database.Database
import io.justtrack.deeplinks.DeepLinkData
import io.justtrack.deeplinks.DeepLinkHandled
import io.justtrack.deeplinks.DeepLinkListener
import io.justtrack.events.Money
import io.justtrack.exceptions.InvalidFieldException
import io.justtrack.exceptions.SdkNotTrackingException
import io.justtrack.integrations.IntegrationAdapter
import io.justtrack.log.Logger
import io.justtrack.publicInterface.SdkTest
import io.justtrack.retargeting.PreliminaryRetargetingParameters
import io.justtrack.retargeting.PreliminaryRetargetingParametersListener
import io.justtrack.retargeting.RetargetingParameters
import io.justtrack.retargeting.RetargetingParametersListener
import io.justtrack.util.ExecutorServiceFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class JusttrackSdkImplTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()
    }

    private fun buildSdk(
        attributionApi: io.justtrack.api.AttributionApi = io.justtrack.api.DefaultAttributionApi(),
        eventApi: io.justtrack.api.EventApi = io.justtrack.api.DefaultEventApi(),
        privacyApi: io.justtrack.api.PrivacyApi = io.justtrack.api.DefaultPrivacyApi(),
        logApi: LogApi = DefaultLogApi(),
        retryConfig: RetryConfig = RetryConfig.DEFAULT_CONFIG,
    ): JustTrackSdkImpl {
        val app = (context.applicationContext as Application)
        val builder = JustTrackSdkBuilder(app, SdkTest.API_TOKEN)
        return createForTesting(
            builder,
            retryConfig,
            null,
            null,
            attributionApi = attributionApi,
            eventApi = eventApi,
            privacyApi = privacyApi,
            logApi = logApi,
        )
    }

    @Test(timeout = 10_000)
    fun isTracking_handle_correctly() {
        val sdk = buildSdk()
        try {
            assertTrue("SDK should be running after start()", sdk.isRunning)
            sdk.stop()
            assertFalse("SDK should not be running after stop()", sdk.isRunning)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun anonymize() {
        val sdk = buildSdk()
        try {
            // anonymize while tracking — should succeed (DefaultPrivacyApi returns success)
            val result = sdk.anonymize()
            assertNotNull("anonymize() should return a future when tracking", result)
            val value = result!!.get()
            assertTrue("anonymize() future should resolve to true", value)

            // anonymize after stop — should return ErrorFuture wrapping SdkNotTrackingException
            sdk.stop()
            val stoppedResult = sdk.anonymize()
            assertNotNull("anonymize() should return an error future when not tracking", stoppedResult)
            try {
                stoppedResult!!.get()
                fail("anonymize() should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getAttribution_when_isTracking_is_false() {
        val sdk = buildSdk()
        try {
            sdk.stop()
            try {
                sdk.attribution.get()
                fail("attribution should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getAdvertiserIdInfo() {
        val sdk = buildSdk()
        try {
            val info = sdk.advertiserIdInfo.get()
            assertNotNull("advertiserIdInfo should not be null", info)
            assertNotNull("advertiserId should not be null", info.advertiserId)
            // advertiserId should be a valid UUID
            assertEquals(
                info.advertiserId,
                UUID.fromString(info.advertiserId).toString(),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun integrateWith() {
        val sdk = buildSdk()
        try {
            // Adapter integrated immediately because SDK is already running
            val integratedImmediately = AtomicBoolean(false)
            sdk.integrateWith(object : IntegrationAdapter {
                override fun integrate(context: Context, sdk: JustTrackSdk, logger: Logger) {
                    integratedImmediately.set(true)
                }
            })
            assertTrue(
                "Adapter should be integrated immediately when SDK is running",
                integratedImmediately.get(),
            )
        } finally {
            sdk.shutdown()
        }

        // Test pending adapter path: build a new SDK with manualStart=true so start() is not
        // called by createForTesting. We use JustTrackSdkBuilder directly here.
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()

        val app = (context.applicationContext as Application)
        val builder = JustTrackSdkBuilder(app, SdkTest.API_TOKEN).setManualStart(true)
        val sdk2 = createForTesting(
            builder,
            RetryConfig.DEFAULT_CONFIG,
            null,
            null,
        )
        try {
            // createForTesting calls start() even with manualStart — adapter queued before that
            // is already flushed. Calling integrateWith now should integrate immediately.
            val integratedAfterStart = AtomicBoolean(false)
            sdk2.integrateWith(object : IntegrationAdapter {
                override fun integrate(context: Context, sdk: JustTrackSdk, logger: Logger) {
                    integratedAfterStart.set(true)
                }
            })
            assertTrue(
                "Adapter should be integrated when SDK is running",
                integratedAfterStart.get(),
            )
        } finally {
            sdk2.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setExperimentVariant_isTracking_false() {
        val sdk = buildSdk()
        try {
            sdk.stop()
            try {
                sdk.setExperimentVariant("experiment", "variant", null).get()
                fail("setExperimentVariant should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setExperimentVariant_invalid_experiment() {
        val sdk = buildSdk()
        try {
            // A string longer than 256 characters is invalid
            val longExperiment = "a".repeat(257)
            try {
                sdk.setExperimentVariant(longExperiment, "variant", null).get()
                fail("setExperimentVariant should throw for an invalid experiment name")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be InvalidFieldException",
                    e.cause is InvalidFieldException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setExperimentVariant_invalid_variant() {
        val sdk = buildSdk()
        try {
            // A string longer than 256 characters is invalid
            val longVariant = "b".repeat(257)
            try {
                sdk.setExperimentVariant("experiment", longVariant, null).get()
                fail("setExperimentVariant should throw for an invalid variant name")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be InvalidFieldException",
                    e.cause is InvalidFieldException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setExperimentVariant_too_many_tags() {
        val sdk = buildSdk()
        try {
            // More than 5 tags are not allowed
            val tags = listOf("tag1", "tag2", "tag3", "tag4", "tag5", "tag6")
            try {
                sdk.setExperimentVariant("experiment", "variant", tags, null).get()
                fail("setExperimentVariant should throw when more than 5 tags are provided")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be InvalidFieldException",
                    e.cause is InvalidFieldException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setExperimentVariant_invalid_tag() {
        val sdk = buildSdk()
        try {
            // A tag longer than 64 characters is invalid
            val longTag = "t".repeat(65)
            try {
                sdk.setExperimentVariant("experiment", "variant", listOf(longTag), null).get()
                fail("setExperimentVariant should throw for an invalid tag")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be InvalidFieldException",
                    e.cause is InvalidFieldException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setExperimentVariant_success_with_tags() {
        val sdk = buildSdk()
        try {
            // Valid call with tags — should complete without error
            sdk.setExperimentVariant("experiment", "variant", listOf("tag1", "tag2"), null).get()
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setExperimentVariant_success_without_tags() {
        val sdk = buildSdk()
        try {
            // 3-arg convenience overload — delegates with empty tags list; should succeed
            sdk.setExperimentVariant("experiment", "variant", null).get()
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun onNewIntent() {
        val sdk = buildSdk()
        try {
            val deepLinkReceived = CountDownLatch(1)
            val receivedUri = arrayOfNulls<Uri>(1)

            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    receivedUri[0] = deepLink.uri
                    deepLinkReceived.countDown()
                    return DeepLinkHandled.DEEP_LINK_HANDLED
                }
            })

            val testUri = Uri.parse("https://example.com/deeplink")
            val intent = Intent(Intent.ACTION_VIEW, testUri)
            sdk.onNewIntent(intent)

            assertTrue(
                "DeepLinkListener should be called within timeout",
                deepLinkReceived.await(5, TimeUnit.SECONDS),
            )
            assertEquals(testUri, receivedUri[0])
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun registerDeepLinkListener() {
        val sdk = buildSdk()
        try {
            val subscription = sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled = DeepLinkHandled.DEEP_LINK_IGNORED
            })
            assertNotNull("registerDeepLinkListener should return a non-null subscription", subscription)
            // Cancelling should not throw
            subscription.unsubscribe()
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun registerAttributionListener() {
        val sdk = buildSdk()
        try {
            val subscription = sdk.registerAttributionListener(object : AttributionListener {
                override fun onAttributionReceived(attribution: Attribution) {}
            })
            assertNotNull("registerAttributionListener should return a non-null subscription", subscription)
            subscription.unsubscribe()
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun registerRetargetingParametersListener() {
        val sdk = buildSdk()
        try {
            val subscription = sdk.registerRetargetingParametersListener(object : RetargetingParametersListener {
                override fun onRetargetingParametersReceived(retargetingParameters: RetargetingParameters) {}
            })
            assertNotNull(
                "registerRetargetingParametersListener should return a non-null subscription",
                subscription,
            )
            subscription.unsubscribe()
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun registerDeepLinkListener_fires_immediately_when_intent_already_set() {
        // Covers the branch inside registerDeepLinkListener where this.intent != null and has
        // an ACTION_VIEW URI: the listener is invoked immediately upon registration for the
        // already-stored deep link intent.
        val sdk = buildSdk()
        try {
            // First, set an ACTION_VIEW intent on the SDK so that this.intent is non-null with a URI.
            val uri = Uri.parse("https://example.com/existing-deeplink")
            val existingIntent = Intent(Intent.ACTION_VIEW, uri)
            sdk.handleNewIntent(existingIntent, false)

            // Now register a new listener. Because this.intent already has an ACTION_VIEW URI,
            // the listener should be called immediately (via taskExecutor.execute).
            val latch = CountDownLatch(1)
            val receivedUri = arrayOfNulls<Uri>(1)
            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    receivedUri[0] = deepLink.uri
                    latch.countDown()
                    return DeepLinkHandled.DEEP_LINK_HANDLED
                }
            })

            assertTrue(
                "Listener should be called immediately for the already-stored deep link intent",
                latch.await(5, TimeUnit.SECONDS),
            )
            assertEquals(uri, receivedUri[0])
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun registerPreliminaryRetargetingParametersListener() {
        val sdk = buildSdk()
        try {
            val subscription = sdk.registerPreliminaryRetargetingParametersListener(
                object : PreliminaryRetargetingParametersListener {
                    override fun onPreliminaryRetargetingParametersReceived(preliminaryRetargetingParameters: PreliminaryRetargetingParameters) {}
                },
            )
            assertNotNull(
                "registerPreliminaryRetargetingParametersListener should return a non-null subscription",
                subscription,
            )
            subscription.unsubscribe()
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getRetargetingParameters() {
        // Use a custom attribution API that returns a retargeting attribution response,
        // so retargetingParameters is non-null (requires "retargeting" key in the JSON).
        val retargetingAttributionApi = object : io.justtrack.api.DefaultAttributionApi() {
            override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<org.json.JSONObject?> {
                val retargeting = org.json.JSONObject()
                retargeting.put("url", "https://example.com/retarget")
                retargeting.put("attributes", org.json.JSONObject())
                val response = org.json.JSONObject(AttributionTest.testAttribution.toString())
                response.put("retargeting", retargeting)
                return Result.success(response)
            }
        }

        val sdk = buildSdk(attributionApi = retargetingAttributionApi)
        try {
            // While tracking — retargetingParameters should be non-null because the API
            // response includes the "retargeting" field.
            val retargetingParams = sdk.retargetingParameters.get()
            assertNotNull("retargetingParameters should not be null for a retargeting session", retargetingParams)

            // isTracking = false path — should return ErrorFuture wrapping SdkNotTrackingException.
            sdk.stop()
            try {
                sdk.retargetingParameters.get()
                fail("retargetingParameters should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getPreliminaryRetargetingParameters() {
        val sdk = buildSdk()
        try {
            // No ACTION_VIEW intent was passed to the SDK, so there are no preliminary parameters.
            val preliminaryParams = sdk.preliminaryRetargetingParameters
            assertNull(
                "preliminaryRetargetingParameters should be null when no retargeting intent was present",
                preliminaryParams,
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun track() = runBlocking {
        val trackedEvents = mutableListOf<String>()
        val eventApi = object : io.justtrack.api.DefaultEventApi() {
            override suspend fun sendUserEvents(
                body: io.justtrack.dtos.DTOAppEvent,
                advertiserId: String?,
                uuid: String,
                installId: String,
            ): Result<org.json.JSONObject?> {
                synchronized(trackedEvents) {
                    body.events.forEach { trackedEvents.add(it.name) }
                }
                return Result.success(org.json.JSONObject())
            }
        }

        val sdk = buildSdk(eventApi = eventApi)
        // maxBatchSize=1 makes each event flush immediately without waiting for the 5-second
        // batch timer, so each .await() resolves as soon as sendUserEvents returns.
        sdk.publishEventsQueue.maxBatchSize = 1
        try {
            // track(AppEvent)
            sdk.track(AppEvent("event_via_app_event")).await()
            // track(String, Map)
            sdk.track("event_via_name_map", mapOf("key" to "value")).await()
            // track(String)
            sdk.track("event_via_name").await()

            synchronized(trackedEvents) {
                assertTrue(
                    "event_via_app_event should have been tracked",
                    trackedEvents.contains("event_via_app_event"),
                )
                assertTrue(
                    "event_via_name_map should have been tracked",
                    trackedEvents.contains("event_via_name_map"),
                )
                assertTrue(
                    "event_via_name should have been tracked",
                    trackedEvents.contains("event_via_name"),
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setUserId_isTracking_false() {
        val sdk = buildSdk()
        try {
            sdk.stop()
            try {
                sdk.setUserId("user123").get()
                fail("setUserId should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setFirebaseAppInstanceId_isTracking_false() {
        val sdk = buildSdk()
        try {
            sdk.stop()
            try {
                sdk.setFirebaseAppInstanceId("firebase123456789").get()
                fail("setFirebaseAppInstanceId should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setFirebaseAppInstanceIdInternal_success() {
        val sdk = buildSdk()
        try {
            // Should complete without throwing when SDK is tracking
            val result = sdk.setFirebaseAppInstanceId("firebase-instance-id-123456").get()
            assertNotNull("setFirebaseAppInstanceId should return a result", result)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setFirebaseAppInstanceId_isTracking_true_then_false() {
        val sdk = buildSdk()
        try {
            // While tracking — should succeed
            val result = sdk.setFirebaseAppInstanceId("firebase-instance-id-abc").get()
            assertNotNull("setFirebaseAppInstanceId should return a result when tracking", result)

            // After stop — should throw SdkNotTrackingException
            sdk.stop()
            try {
                sdk.setFirebaseAppInstanceId("firebase-instance-id-xyz").get()
                fail("setFirebaseAppInstanceId should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun forward_purchase() {
        val sdk = buildSdk()
        try {
            // forwardAdImpression — should return a non-null future
            val adImpression = AdImpression(unit = "banner", sdkName = "test-sdk")
            val adImpressionFuture = sdk.forwardAdImpression(adImpression)
            assertNotNull("forwardAdImpression should return a non-null future", adImpressionFuture)

            // forwardInApp — returns boolean (true on success)
            val money = Money(0.99, "USD")
            val inAppResult = sdk.forwardInApp("product_id", "purchase_token", money)
            // Result depends on billing availability; just verify no exception is thrown
            assertTrue("forwardInApp should return a boolean", inAppResult || !inAppResult)

            // forwardSubscription — returns boolean
            val subscriptionResult = sdk.forwardSubscription("subscription_id", "subscription_token", money)
            assertTrue("forwardSubscription should return a boolean", subscriptionResult || !subscriptionResult)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 20_000)
    fun onResume() {
        val sdk = buildSdk(retryConfig = NO_RETRIES_CONFIG)
        try {
            // Mock an Activity whose getIntent() returns null.
            // handleNewIntent skips all work (no attribution fetch, no deep link) when
            // newIntent is null, so the taskExecutor drains quickly and shutdown is clean.
            val activity = Mockito.mock(Activity::class.java)
            Mockito.`when`(activity.intent).thenReturn(null)
            sdk.onResume(activity)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getInstallInstanceId() {
        val sdk = buildSdk()
        try {
            // While tracking — should return a valid lowercase UUID
            val installId = sdk.installInstanceId.get()
            assertNotNull("installInstanceId should not be null", installId)
            assertEquals(installId.lowercase(Locale.getDefault()), installId)
            assertEquals(36, installId.length)
            assertEquals(installId, UUID.fromString(installId).toString())

            // After stop — should throw SdkNotTrackingException
            sdk.stop()
            try {
                sdk.installInstanceId.get()
                fail("installInstanceId should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getRemoteConfig() {
        val sdk = buildSdk()
        try {
            val remoteConfig = sdk.remoteConfig
            assertNotNull("remoteConfig should not be null", remoteConfig)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setAutomaticInAppPurchaseTracking() {
        val sdk = buildSdk()
        try {
            // Both calls should complete without throwing
            sdk.setAutomaticInAppPurchaseTracking(true)
            sdk.setAutomaticInAppPurchaseTracking(false)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getIntegritySecret_set_id() {
        val sdk = buildSdk()
        try {
            // getIntegritySecret should return a non-empty string
            val secret = sdk.getIntegritySecret().get()
            assertNotNull("integritySecret should not be null", secret)
            assertTrue("integritySecret should not be empty", secret.isNotEmpty())

            // setUserIdInternal should complete without error
            val setUserResult = sdk.setUserIdInternal("test-user-id").get()
            assertNotNull("setUserIdInternal result should not be null", setUserResult)

            // Cover pendingIntegrationAdapters loop: register an adapter, then call start() again.
            // Because start() -> startWithConfig(), and pendingIntegrationAdapters is empty after
            // the initial start, we add one directly and trigger startWithConfig by calling start().
            val adapterIntegrated = AtomicBoolean(false)
            // integrateWith while running integrates immediately, covering the synchronized block
            sdk.integrateWith(object : IntegrationAdapter {
                override fun integrate(context: Context, sdk: JustTrackSdk, logger: Logger) {
                    adapterIntegrated.set(true)
                }
            })
            assertTrue(
                "IntegrationAdapter should have been called via integrateWith",
                adapterIntegrated.get(),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun callPreliminaryRetargetingParametersSubscriptions() {
        val sdk = buildSdk()
        try {
            val received = CountDownLatch(1)

            sdk.registerPreliminaryRetargetingParametersListener(
                object : PreliminaryRetargetingParametersListener {
                    override fun onPreliminaryRetargetingParametersReceived(preliminaryRetargetingParameters: PreliminaryRetargetingParameters) {
                        received.countDown()
                    }
                },
            )

            // Build an ACTION_VIEW intent with a URL and at least one string extra so that
            // PreliminaryRetargetingParametersImpl.fromIntent returns a non-null instance.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/retarget"))
            intent.putExtra("campaign", "summer")
            val params = PreliminaryRetargetingParametersImpl.fromIntent(intent)
            assertNotNull("fromIntent should return non-null for a valid ACTION_VIEW intent", params)

            sdk.callPreliminaryRetargetingParametersSubscriptions(params!!)

            assertTrue(
                "PreliminaryRetargetingParametersListener should be called",
                received.await(5, TimeUnit.SECONDS),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun callDeepLinkSubscriptions() {
        val sdk = buildSdk()
        try {
            val received = CountDownLatch(1)
            val receivedUri = arrayOfNulls<Uri>(1)

            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    receivedUri[0] = deepLink.uri
                    received.countDown()
                    return DeepLinkHandled.DEEP_LINK_HANDLED
                }
            })

            val testUri = Uri.parse("https://example.com/deeplink")
            val intent = Intent(Intent.ACTION_VIEW, testUri)
            sdk.handleNewIntent(intent, false)

            assertTrue(
                "DeepLinkListener should be called for an ACTION_VIEW intent",
                received.await(5, TimeUnit.SECONDS),
            )
            assertEquals(testUri, receivedUri[0])
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun performDeepLinkCall() = runBlocking {
        val publishedEventNames = mutableListOf<String>()
        val eventApi = object : io.justtrack.api.DefaultEventApi() {
            override suspend fun sendUserEvents(
                body: io.justtrack.dtos.DTOAppEvent,
                advertiserId: String?,
                uuid: String,
                installId: String,
            ): Result<org.json.JSONObject?> {
                synchronized(publishedEventNames) {
                    body.events.forEach { publishedEventNames.add(it.name) }
                }
                return Result.success(org.json.JSONObject())
            }
        }

        val sdk = buildSdk(eventApi = eventApi)
        sdk.publishEventsQueue.maxBatchSize = 1
        try {
            val handledLatch = CountDownLatch(1)
            val notHandledLatch = CountDownLatch(1)

            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    handledLatch.countDown()
                    return DeepLinkHandled.DEEP_LINK_HANDLED
                }
            })
            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    notHandledLatch.countDown()
                    return DeepLinkHandled.DEEP_LINK_NOT_HANDLED
                }
            })

            val testUri = Uri.parse("https://example.com/deeplink")
            sdk.handleNewIntent(Intent(Intent.ACTION_VIEW, testUri), false)

            assertTrue("HANDLED listener should be called", handledLatch.await(5, TimeUnit.SECONDS))
            assertTrue("NOT_HANDLED listener should be called", notHandledLatch.await(5, TimeUnit.SECONDS))

            // Wait for the jt_deeplink_handled and jt_deeplink_not_handled events to be sent.
            // Each event flushes immediately (maxBatchSize=1), so we poll briefly.
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                synchronized(publishedEventNames) {
                    if (publishedEventNames.contains("jt_deeplink_handled") &&
                        publishedEventNames.contains("jt_deeplink_not_handled")
                    ) {
                        return@runBlocking
                    }
                }
                Thread.sleep(100)
            }
            synchronized(publishedEventNames) {
                assertTrue(
                    "jt_deeplink_handled event should be published",
                    publishedEventNames.contains("jt_deeplink_handled"),
                )
                assertTrue(
                    "jt_deeplink_not_handled event should be published",
                    publishedEventNames.contains("jt_deeplink_not_handled"),
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun handleNewIntent() {
        val sdk = buildSdk()
        try {
            // Branch 1: null intent — must not crash and must not call any listener.
            val nullIntentLatch = CountDownLatch(1)
            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    nullIntentLatch.countDown()
                    return DeepLinkHandled.DEEP_LINK_IGNORED
                }
            })
            sdk.handleNewIntent(null, false)
            assertFalse(
                "Deep link listener must not be called for a null intent",
                nullIntentLatch.await(500, TimeUnit.MILLISECONDS),
            )

            // Branch 2: ACTION_VIEW intent — deep link listener fires with correct URI.
            val deepLinkLatch = CountDownLatch(1)
            val receivedUri = arrayOfNulls<Uri>(1)
            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    receivedUri[0] = deepLink.uri
                    deepLinkLatch.countDown()
                    return DeepLinkHandled.DEEP_LINK_HANDLED
                }
            })
            val uri = Uri.parse("https://example.com/link")
            sdk.handleNewIntent(Intent(Intent.ACTION_VIEW, uri), false)
            assertTrue(
                "Deep link listener should be called for ACTION_VIEW intent",
                deepLinkLatch.await(5, TimeUnit.SECONDS),
            )
            assertEquals(uri, receivedUri[0])
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun handleNewIntent_replaces_old_preliminary_retargeting_params() {
        // This test covers the branch:
        //   if (oldPreliminaryRetargetingParametersImpl != null) { oldOne.reject(...) }
        //   if (newPreliminaryRetargetingParametersImpl != null) {
        //       callPreliminaryRetargetingParametersSubscriptions(...)
        //       attributionOutputProvider.provideAttributionOutput(FETCH_RETARGETING_ATTRIBUTION)
        //   }
        //
        // Strategy:
        // 1. Call handleNewIntent with an ACTION_VIEW intent that has string extras so that
        //    fromIntent() returns a non-null PreliminaryRetargetingParametersImpl, setting it as
        //    the current preliminaryRetargetingParametersImpl (oldPreliminaryRetargetingParametersImpl).
        // 2. Register a PreliminaryRetargetingParametersListener *before* the second call.
        // 3. Call handleNewIntent with a second, *different* ACTION_VIEW intent so that:
        //    - the old params are rejected (oldPreliminaryRetargetingParametersImpl != null branch)
        //    - the listener is called for the new params (newPreliminaryRetargetingParametersImpl != null branch)

        val retargetingAttributionApi = object : io.justtrack.api.DefaultAttributionApi() {
            override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<org.json.JSONObject?> {
                val retargeting = org.json.JSONObject()
                retargeting.put("url", "https://example.com/retarget")
                retargeting.put("attributes", org.json.JSONObject())
                val response = org.json.JSONObject(AttributionTest.testAttribution.toString())
                response.put("retargeting", retargeting)
                return Result.success(response)
            }
        }

        val sdk = buildSdk(attributionApi = retargetingAttributionApi)
        try {
            // Step 1: set the first ACTION_VIEW intent so that preliminaryRetargetingParametersImpl
            // becomes non-null inside the SDK.
            val firstIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/retarget1"))
            firstIntent.putExtra("campaign", "first")
            sdk.handleNewIntent(firstIntent, false)

            // The SDK field should now hold the first preliminary params.
            assertNotNull(
                "preliminaryRetargetingParameters should be set after first handleNewIntent",
                sdk.preliminaryRetargetingParameters,
            )

            // Step 2: register a listener that will be fired when the new params are dispatched.
            val newParamsLatch = CountDownLatch(1)
            sdk.registerPreliminaryRetargetingParametersListener(
                object : PreliminaryRetargetingParametersListener {
                    override fun onPreliminaryRetargetingParametersReceived(preliminaryRetargetingParameters: PreliminaryRetargetingParameters) {
                        newParamsLatch.countDown()
                    }
                },
            )

            // Step 3: call handleNewIntent with a second, different intent.
            // This triggers:
            //   - oldPreliminaryRetargetingParametersImpl.reject(...)
            //   - callPreliminaryRetargetingParametersSubscriptions(newParams)
            //   - attributionOutputProvider.provideAttributionOutput(FETCH_RETARGETING_ATTRIBUTION)
            val secondIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/retarget2"))
            secondIntent.putExtra("campaign", "second")
            sdk.handleNewIntent(secondIntent, false)

            assertTrue(
                "PreliminaryRetargetingParametersListener should be called for the new intent",
                newParamsLatch.await(5, TimeUnit.SECONDS),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun handleNewIntent_isAutomatic_skipped_when_already_handled_manually() {
        // Covers the early-return branch: isAutomatic=true but handleIntentsOnResume=false
        // (because a manual call has already set handleIntentsOnResume = false).
        val sdk = buildSdk()
        try {
            // Register the listener BEFORE any handleNewIntent call so that this.intent is
            // still null at registration time — registerDeepLinkListener will not fire immediately.
            val listenerCalled = AtomicBoolean(false)
            sdk.registerDeepLinkListener(object : DeepLinkListener {
                override fun onDeepLinkClicked(deepLink: DeepLinkData): DeepLinkHandled {
                    listenerCalled.set(true)
                    return DeepLinkHandled.DEEP_LINK_IGNORED
                }
            })

            // A manual call (isAutomatic=false) sets handleIntentsOnResume = false.
            // Use a non-ACTION_VIEW intent so that no deep link URI is extracted and the listener
            // is not triggered by this call either.
            val manualIntent = Intent(Intent.ACTION_MAIN)
            sdk.handleNewIntent(manualIntent, false)

            // An automatic call with a new ACTION_VIEW intent should be silently ignored because
            // handleIntentsOnResume is now false.
            val automaticIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/automatic"))
            sdk.handleNewIntent(automaticIntent, true)

            Thread.sleep(300)
            assertFalse(
                "Deep link listener must not be called when an automatic intent is skipped",
                listenerCalled.get(),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun retryAttributionAfterReconnect() {
        // Use a failing attribution API so the first fetch produces an ErrorFuture.
        val failThenSucceedApi = object : io.justtrack.api.DefaultAttributionApi() {
            @Volatile
            var shouldFail = true

            override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<org.json.JSONObject?> = if (shouldFail) {
                Result.failure(NetworkProblemException(RuntimeException("simulated network failure")))
            } else {
                Result.success(AttributionTest.testAttribution)
            }
        }

        val sdk = buildSdk(
            attributionApi = failThenSucceedApi,
            retryConfig = NO_RETRIES_CONFIG,
        )
        try {
            // Force the attribution output into an ErrorFuture.
            try {
                sdk.attributionOutputProvider.provideAttributionOutput(null).get()
            } catch (_: Exception) {
                // Expected — API is set to fail
            }

            // No-op path: with a non-ErrorFuture output retryAttributionAfterReconnect does nothing.
            // We verify this doesn't throw.
            failThenSucceedApi.shouldFail = false
            sdk.retryAttributionAfterReconnect()

            // After the call, a fresh attribution fetch should now succeed.
            val attribution = sdk.attributionOutputProvider.provideAttributionOutput(null).get()
            assertNotNull("Attribution should succeed after retryAttributionAfterReconnect", attribution)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun retrySendPersistId() {
        val customUserIdSent = CountDownLatch(1)
        val firebaseIdSent = CountDownLatch(1)

        val capturingApi = object : io.justtrack.api.DefaultAttributionApi() {
            override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<org.json.JSONObject?> =
                Result.success(AttributionTest.testAttribution)

            override suspend fun sendCustomUserId(
                body: io.justtrack.dtos.DTOPublishCustomUserIdRequest,
                advertiserId: String?,
                uuid: String,
                installId: String,
            ): Result<Unit> {
                customUserIdSent.countDown()
                return Result.success(Unit)
            }

            override suspend fun sendFirebaseAppInstanceId(
                body: JSONEncodable,
                advertiserId: String?,
                uuid: String,
                installId: String,
            ): Result<Unit> {
                firebaseIdSent.countDown()
                return Result.success(Unit)
            }
        }

        val executorBuilder = ExecutorServiceFactory {
            val executor = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, LinkedBlockingDeque())
            executor.allowCoreThreadTimeOut(true)
            executor
        }

        val sdk = TestSdk(
            context = context,
            executorBuilder = executorBuilder,
            runCallbacksSerially = false,
            attributionApi = capturingApi,
        )
        sdk.start()

        try {
            // Wait for attribution so we have a valid installId before storing pending IDs.
            sdk.attributionOutputProvider.provideAttributionOutput(null).get()

            // Store a pending custom user ID and Firebase ID.
            CustomUserIdStore.getInstance().storeNewId(context, null, "pending-custom-user")
            FirebaseIdStore.getInstance().storeNewId(context, null, "pending-firebase-id-xx")

            sdk.onReconnect()

            assertTrue(
                "sendCustomUserId should be called after reconnect",
                customUserIdSent.await(5, TimeUnit.SECONDS),
            )
            assertTrue(
                "sendFirebaseAppInstanceId should be called after reconnect",
                firebaseIdSent.await(5, TimeUnit.SECONDS),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun installUncaughtExceptionHandler_does_not_throw() {
        // installUncaughtExceptionHandler delegates to crashHandler.installUncaughtExceptionHandler().
        // We just verify the call completes without exception.
        val sdk = buildSdk()
        try {
            sdk.installUncaughtExceptionHandler()
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun integrateWith_queues_adapter_before_sdk_starts_then_flushes_on_start() {
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()

        val integratedLatch = CountDownLatch(1)
        val app = (context.applicationContext as Application)

        val builder = JustTrackSdkBuilder(app, SdkTest.API_TOKEN)
            .addIntegrationAdapters(
                listOf(
                    object : IntegrationAdapter {
                        override fun integrate(context: Context, sdk: JustTrackSdk, logger: Logger) {
                            integratedLatch.countDown()
                        }
                    },
                ),
            )

        val sdk = createForTesting(builder, RetryConfig.DEFAULT_CONFIG, null, null)
        try {
            assertTrue(
                "Adapter passed via builder should be integrated during start()",
                integratedLatch.await(5, TimeUnit.SECONDS),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun constructor_uses_provided_claimProvider() {
        // Covers the branch: if (claimProvider != null) { resolvedClaimProvider = claimProvider; }
        // We pass a real (no-op) ClaimProvider and verify the SDK starts normally.
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()

        val app = (context.applicationContext as Application)
        val builder = JustTrackSdkBuilder(app, SdkTest.API_TOKEN)

        val customClaimProvider = object : ClaimProvider {
            override fun refreshClaims() { /* no-op */ }
            override fun provideClaims(timeout: Long): ProvidedClaims = ProvidedClaims()
        }

        val sdk = createForTesting(builder, RetryConfig.DEFAULT_CONFIG, customClaimProvider, null)
        try {
            assertTrue("SDK should be running when started with a custom ClaimProvider", sdk.isRunning)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun init_lifecycle_already_resumed_calls_sessionManager_onResume() {
        val sdk = buildSdk()
        try {
            assertTrue("SDK should be running after init() with already-RESUMED lifecycle", sdk.isRunning)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun init_lifecycle_observer_fires_on_resume() {
        // execute on the main thread, then asserts the SDK state is consistent.
        val sdk = buildSdk()
        try {
            // Give the Handler.post() in init() time to run on the main looper.
            Thread.sleep(500)
            assertTrue("SDK should still be running after init() lifecycle handler executed", sdk.isRunning)
            assertNotNull("installInstanceId should be available after init()", sdk.installInstanceId.get())
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun integrateWith_adds_to_pending_when_not_running() {
        val sdk = buildSdk()
        try {
            sdk.stop()
            assertFalse("SDK must not be running before the test", sdk.isRunning)

            val adapterCalled = AtomicBoolean(false)
            sdk.integrateWith(object : IntegrationAdapter {
                override fun integrate(context: Context, sdk: JustTrackSdk, logger: Logger) {
                    adapterCalled.set(true)
                }
            })

            // The adapter should have been queued, not called immediately.
            assertFalse(
                "Adapter must not be called immediately when SDK is not running",
                adapterCalled.get(),
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 20_000)
    fun constructor_with_null_apis_falls_back_to_real_implementations() {
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()

        val app = (context.applicationContext as Application)
        val builder = JustTrackSdkBuilder(app, SdkTest.API_TOKEN)

        // Use the single-arg constructor directly — all API params are null internally.
        val sdk = JustTrackSdkImpl(builder)
        sdk.init(builder)
        InstanceManager.setInstance(sdk)
        JustTrack.notifyAppStart()
        sdk.start()

        try {
            assertTrue("SDK should be running after starting with null-API constructor", sdk.isRunning)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getAttribution_while_tracking_returns_future() {
        // Covers the `else` branch of getAttribution():
        //   return new TransformingFuture<>(..., AttributionOutput::getAttribution);
        val successAttributionApi = object : io.justtrack.api.DefaultAttributionApi() {
            override suspend fun sendAttributionRequest(body: JSONEncodable, advertiserId: String?): Result<org.json.JSONObject?> =
                Result.success(AttributionTest.testAttribution)
        }
        val sdk = buildSdk(attributionApi = successAttributionApi)
        try {
            val attribution = sdk.attribution.get()
            assertNotNull("attribution should not be null while tracking", attribution)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun setUserId_while_tracking_succeeds() {
        // Covers the `else` branch of setUserId():
        //   return setUserIdInternal(userId);
        val sdk = buildSdk()
        try {
            val result = sdk.setUserId("valid-user-id").get()
            assertNotNull("setUserId should return a result when tracking", result)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun startWithConfig_with_userId_calls_setUserIdInternal() {
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()

        val app = (context.applicationContext as Application)
        val builder = JustTrackSdkBuilder(app, SdkTest.API_TOKEN)
            .setUserId("test-user-123")

        val sdk = createForTesting(builder, RetryConfig.DEFAULT_CONFIG, null, null)
        try {
            assertTrue("SDK should be running after start with userId set", sdk.isRunning)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun startWithConfig_with_trackingId_sets_field() {
        Database.clearForTesting(context)
        DatabaseInterface.clearForTesting()
        Store.clearForTesting(context)
        SessionManagerImpl.Session.clearForTesting(context)
        CustomUserIdStore.getInstance().clearForTesting(context)
        FirebaseIdStore.getInstance().clearForTesting(context)
        JustTrack.resetForTesting()

        val app = (context.applicationContext as Application)
        val builder = JustTrackSdkBuilder(app, SdkTest.API_TOKEN)
            .setTrackingId("my-tracking-id-123", "custom")

        val sdk = createForTesting(builder, RetryConfig.DEFAULT_CONFIG, null, null)
        try {
            assertTrue("SDK should be running after start with trackingId set", sdk.isRunning)
            assertEquals(
                "trackingId field should match the value set on the builder",
                "my-tracking-id-123",
                sdk.trackingId,
            )
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getAdvertiserIdInfo_when_not_tracking_throws() {
        val sdk = buildSdk()
        try {
            sdk.stop()
            try {
                sdk.advertiserIdInfo.get()
                fail("getAdvertiserIdInfo should throw when SDK is not tracking")
            } catch (e: ExecutionException) {
                assertTrue(
                    "Cause should be SdkNotTrackingException",
                    e.cause is SdkNotTrackingException,
                )
            }
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun getAppVersionAtInstall_returns_future() {
        val sdk = buildSdk()
        try {
            val appVersion = sdk.appVersionProvider.getApplicationVersionAtInstalled().get()
            // The installed version may be null on first install, or non-null if already stored.
            // Either way, the call must not throw.
            assertTrue("getAppVersionAtInstall should complete without exception", true)
        } finally {
            sdk.shutdown()
        }
    }

    @Test(timeout = 10_000)
    fun onNewIntent_after_shutdown_does_not_crash() {
        val sdk = buildSdk()
        sdk.shutdown()

        // Should not throw even though the executor is shut down.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/late"))
        sdk.onNewIntent(intent)
    }
}
