package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.justtrack.log.Logger;
import io.justtrack.log.LoggerFieldsBuilder;

class ClaimProviderImpl implements ClaimProvider {
    private static final long CLAIM_EXPIRE_DURATION_MS = 10 * 60 * 1000;

    private final @NonNull Logger logger;
    private @Nullable AsyncFuture<String> ipv4Claim;
    private long ipv4ClaimExpiresAt;
    private @Nullable AsyncFuture<String> ipv6Claim;
    private long ipv6ClaimExpiresAt;

    ClaimProviderImpl(@NonNull Logger logger) {
        this.logger = logger;
        this.ipv4Claim = null;
        this.ipv4ClaimExpiresAt = 0;
        this.ipv6Claim = null;
        this.ipv6ClaimExpiresAt = 0;
    }

    @Override
    // Deadlock-Safety: This only updates some fields and spawns new tasks which don't take any
    // additional locks
    public synchronized void refreshClaims(@NonNull BaseJustTrackSdk sdk) {
        long now = System.currentTimeMillis();
        if (ipv4ClaimExpiresAt < now) {
            ipv4Claim = null;
        }
        if (ipv6ClaimExpiresAt < now) {
            ipv6Claim = null;
        }

        if (ipv4Claim == null) {
            ipv4Claim = sdk.spawnFetchClaimTask(IPProtocol.IPv4);
            ipv4ClaimExpiresAt = now + CLAIM_EXPIRE_DURATION_MS;
        }
        if (ipv6Claim == null) {
            ipv6Claim = sdk.spawnFetchClaimTask(IPProtocol.IPv6);
            ipv6ClaimExpiresAt = now + CLAIM_EXPIRE_DURATION_MS;
        }
    }

    @NonNull
    @Override
    public ProvidedClaims provideClaims(long timeout) {
        ProvidedClaims result = new ProvidedClaims();

        long tookIPv4 = provideClaim(result, ipv4Claim, "IPv4", timeout);
        provideClaim(result, ipv6Claim, "IPv6", timeout - tookIPv4);

        return result;
    }

    private long provideClaim(@NonNull ProvidedClaims result, @Nullable AsyncFuture<String> claim, @NonNull String type, long timeout) {
        long start = System.currentTimeMillis();
        if (claim != null) {
            try {
                @Nullable String claimValue = claim.get(timeout, TimeUnit.MILLISECONDS);
                if (claimValue != null) {
                    result.addClaim(claimValue);
                }
            } catch (TimeoutException | CancellationException | InterruptedException exception) {
                result.setTimedOut();
                logger.warn("Getting " + type + " claim timed out", new LoggerFieldsBuilder().with("exception", exception));
            } catch (Exception exception) {
                if (FetchClaimErrorClassifier.isUnreachableException(exception)) {
                    logger.debug("Getting " + type + " claim failed, protocol is not supported",
                            new LoggerFieldsBuilder().with("exception", exception));
                } else {
                    logger.warn("Getting " + type + " claim failed", exception);
                }
            }
        }

        return System.currentTimeMillis() - start;
    }
}
