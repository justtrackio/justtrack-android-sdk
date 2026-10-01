package io.justtrack.publicInterface;

import static io.justtrack.UtilsKt.getBadResponseBody;

import android.app.Application;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.justtrack.AsyncFuture;
import io.justtrack.AttributionException;
import io.justtrack.JustTrackSdk;
import io.justtrack.JustTrackSdkBuilder;
import io.justtrack.TestLoggerImpl;
import io.justtrack.attribution.Attribution;
import io.justtrack.exceptions.SdkNotTrackingException;

@RunWith(AndroidJUnit4.class)
public class ErrorTest {
    private static final String API_TOKEN = "sandbox-thisIsInvalid";
    private JustTrackSdk sdk = null;
    private final TestLoggerImpl logger = new TestLoggerImpl();

    @Before
    public void createSdk() {
        Assert.assertNull(sdk);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        JustTrackSdkBuilder builder = new JustTrackSdkBuilder((Application) context.getApplicationContext(), API_TOKEN);
        builder = builder.setLogger(logger);
        sdk = builder.build();
        Assert.assertNotNull(sdk);
    }

    @After
    public void destroySdk() {
        Assert.assertNotNull(sdk);
        sdk.shutdown();
        sdk = null;
    }

    @Test(timeout = 3000L)
    public void attribute() throws ExecutionException, InterruptedException, TimeoutException, SdkNotTrackingException {
        AsyncFuture<Attribution> attributionResponse = sdk.getAttribution();

        try {
            attributionResponse.get(3, TimeUnit.SECONDS);
            Assert.fail("Shouldn't have reached reached this point, expected failure");
        } catch (ExecutionException exception) {
            if (!(exception.getCause() instanceof AttributionException)) {
                throw exception;
            }
            AttributionException cause = (AttributionException) exception.getCause();
            String message = cause.getMessage();
            Assert.assertNotNull(message);
            Assert.assertTrue(message.contains("Attribution can not be performed with an invalid API token"));
            Assert.assertTrue(cause.wasApiTokenInvalid());
            String messageResponseBody = getBadResponseBody(cause.getCause());
            Assert.assertEquals("{\"err\":\"invalid token\"}", messageResponseBody);
            logger.assertHasError(true);
            return;
        }

        Assert.fail("Should not have been reached");
    }
}
