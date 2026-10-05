package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The upstream's hostname does not resolve: the "container is gone from oj-net" case
 * (".invalid" is reserved and never resolves, RFC 2606).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "oj.gateway.submission-uri=http://submission-service.invalid:8000"})
class UpstreamUnresolvableTest {

    @LocalServerPort
    int port;

    @Test
    void unresolvableUpstreamReturns503() {
        WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build()
                .get().uri("/api/v1/languages").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.message").isEqualTo("Service temporarily unavailable");
    }
}
