package io.justtrack

import io.justtrack.events.MetricUnit
import java.util.Collections

internal enum class IPProtocol(
    val route: Environment.Route,
    val requestName: String,
    val claimDurationMetric: Metric,
) {
    IPv4(
        Environment.Route.SIGN_IP_V4,
        HttpClientImpl.SIGN_IPV4_REQUEST_NAME,
        Metric(
            "ClaimDuration",
            Collections.singletonMap("Protocol", "IPv4"),
            MetricUnit.MILLISECONDS,
        ),
    ),
    IPv6(
        Environment.Route.SIGN_IP_V6,
        HttpClientImpl.SIGN_IPV6_REQUEST_NAME,
        Metric(
            "ClaimDuration",
            Collections.singletonMap("Protocol", "IPv6"),
            MetricUnit.MILLISECONDS,
        ),
    ),
}
