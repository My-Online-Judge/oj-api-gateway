package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

// @AutoConfigureObservability: Boot's test support otherwise swaps the Prometheus registry for a
// no-op one, and /actuator/prometheus would be missing for reasons unrelated to this test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@AutoConfigureObservability
class ManagementPortTest {

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    private WebTestClient clientFor(int p) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + p).build();
    }

    @Test
    void healthIsServedOnTheManagementPort() {
        clientFor(managementPort).get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void prometheusIsServedOnTheManagementPort() {
        clientFor(managementPort).get().uri("/actuator/prometheus").exchange().expectStatus().isOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/prometheus", "/actuator/env"})
    void actuatorIsNotReachableOnThePublicPort(String path) {
        clientFor(port).get().uri(path).exchange().expectStatus().isNotFound();
    }
}
