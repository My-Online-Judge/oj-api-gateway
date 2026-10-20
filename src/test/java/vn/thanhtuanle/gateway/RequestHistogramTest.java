package vn.thanhtuanle.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * HighLatency alerts on {@code histogram_quantile} over {@code http_server_requests_seconds_bucket}, so the
 * timer must publish buckets. Without {@code percentiles-histogram} it exports only count, sum and max.
 */
@AutoConfigureObservability
class RequestHistogramTest extends GatewayTestSupport {

    @Autowired
    MeterRegistry registry;

    @Test
    void httpServerRequestsPublishesHistogramBuckets() {
        client.get().uri("/api/v1/languages").exchange().expectStatus().isOk();

        await().untilAsserted(() -> {
            var timer = registry.find("http.server.requests").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.takeSnapshot().histogramCounts()).isNotEmpty();
        });
    }
}
