package io.justtrack;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ValueFutureTest {
    @Test
    public void futureTest() throws ExecutionException, InterruptedException, TimeoutException {
        Future<String> resultFuture = new ValueFuture<>("result");
        Assert.assertEquals("result", resultFuture.get());
        Assert.assertEquals("result", resultFuture.get(1, TimeUnit.MILLISECONDS));
        Assert.assertFalse(resultFuture.isCancelled());
        Assert.assertTrue(resultFuture.isDone());
        Assert.assertFalse(resultFuture.cancel(true));
    }
}
