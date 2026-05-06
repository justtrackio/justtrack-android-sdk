package io.justtrack;

import androidx.annotation.NonNull;

import java.util.Collections;

import io.justtrack.events.MetricUnit;

enum IPProtocol {
    IPv4(
            Environment.Route.SIGN_IP_V4,
            HttpClientImpl.SIGN_IPV4_REQUEST_NAME,
            new Metric(
                    "ClaimDuration",
                    Collections.singletonMap("Protocol", "IPv4"),
                    MetricUnit.MILLISECONDS
            )
    ),
    IPv6(
            Environment.Route.SIGN_IP_V6,
            HttpClientImpl.SIGN_IPV6_REQUEST_NAME,
            new Metric(
                    "ClaimDuration",
                    Collections.singletonMap("Protocol", "IPv6"),
                    MetricUnit.MILLISECONDS
            )
    );

    private final @NonNull Environment.Route route;
    private final @NonNull String requestName;
    private final @NonNull Metric claimDurationMetric;

    IPProtocol(@NonNull Environment.Route route, @NonNull String requestName, @NonNull Metric claimDurationMetric) {
        this.route = route;
        this.requestName = requestName;
        this.claimDurationMetric = claimDurationMetric;
    }

    @NonNull
    Environment.Route getRoute() {
        return route;
    }

    @NonNull
    public Metric getClaimDurationMetric() {
        return claimDurationMetric;
    }

    @NonNull
    public String getRequestName() {
        return requestName;
    }
}
