package vn.thanhtuanle.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vn.thanhtuanle.oj.common.client.ClientFingerprint;

import static org.assertj.core.api.Assertions.assertThat;

class AccessBanFilterTest extends GatewayTestSupport {

    private static final String UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36";

    @Autowired
    MeterRegistry registry;

    @Test
    void aBannedDeviceGets403InTheApiResponseShapeAndNeverReachesTheUpstream() {
        bans.banned.add("device:stolen-laptop");

        client.get().uri("/api/v1/problems").header("X-Device-Id", "stolen-laptop").exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.message").isEqualTo("Access from this address or device is banned");
        assertThat(UPSTREAM.received()).isEmpty();
    }

    @Test
    void withoutADeviceIdTheBanMatchesTheUserAgentHash() {
        bans.banned.add("device:" + ClientFingerprint.deviceHash(null, UA));

        client.get().uri("/api/v1/problems").header("User-Agent", UA).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void aBannedIpIsTheSocketPeerNotAClaimedForwardingHeader() {
        bans.banned.add("ip:127.0.0.1");

        client.get().uri("/api/v1/problems").header("X-Forwarded-For", "8.8.8.8").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void anUnbannedClientPassesThrough() {
        bans.banned.add("device:someone-else");

        client.get().uri("/api/v1/problems").header("X-Device-Id", "my-laptop").exchange()
                .expectStatus().isOk();
    }

    @Test
    void anUnreachableBanStoreFailsOpen() {
        bans.unavailable = true;

        client.get().uri("/api/v1/problems").exchange().expectStatus().isOk();
    }

    @Test
    void everyRefusedRequestIsCounted() {
        bans.banned.add("device:stolen-laptop");
        double before = registry.counter("oj.request.banned").count();

        client.get().uri("/api/v1/problems").header("X-Device-Id", "stolen-laptop").exchange()
                .expectStatus().isForbidden();

        assertThat(registry.counter("oj.request.banned").count()).isEqualTo(before + 1);
    }
}
