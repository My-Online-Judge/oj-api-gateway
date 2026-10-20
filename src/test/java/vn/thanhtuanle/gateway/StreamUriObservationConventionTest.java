package vn.thanhtuanle.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Every request the gateway proxies has no handler pattern, so it is tagged {@code uri=UNKNOWN}. An SSE verdict
 * stream lasts as long as judging; lumped in with the rest it would keep HighLatency firing for job api-gateway.
 */
@AutoConfigureObservability
class StreamUriObservationConventionTest extends GatewayTestSupport {

    @Autowired
    MeterRegistry registry;

    @Test
    void aVerdictStreamGetsItsOwnUriTag() {
        client.get().uri("/api/v1/submissions/abc/stream").exchange().expectStatus().isOk();

        await().untilAsserted(() -> assertThat(
                registry.find("http.server.requests").tag("uri", "/api/v1/submissions/{id}/stream").timer())
                .isNotNull());
    }

    @Test
    void otherProxiedRequestsKeepTheDefaultUri() {
        client.get().uri("/api/v1/submissions/abc").exchange().expectStatus().isOk();

        await().untilAsserted(() -> assertThat(
                registry.find("http.server.requests").tag("uri", "UNKNOWN").tag("method", "GET").timer())
                .isNotNull());
        assertThat(registry.find("http.server.requests").tag("uri", "/api/v1/submissions/{id}/stream").timers())
                .isEmpty();
    }
}
