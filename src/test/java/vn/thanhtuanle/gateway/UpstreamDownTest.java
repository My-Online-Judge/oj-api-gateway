package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/** Nothing listens on the monolith's port: the "judge-api stopped" case. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class UpstreamDownTest {

    @DynamicPropertySource
    static void routeToAClosedPort(DynamicPropertyRegistry registry) {
        registry.add("oj.gateway.monolith-uri", () -> "http://127.0.0.1:" + freePort());
    }

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @LocalServerPort
    int port;

    @Test
    void monolithDownReturns503InTheApiResponseShape() {
        WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build()
                .get().uri("/api/v1/languages").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.message").isEqualTo("Service temporarily unavailable")
                .jsonPath("$.timestamp").value(ts -> assertThat((String) ts).matches("\\d{14}"));
    }
}
