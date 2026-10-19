package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Under the tls profile the API port serves TLS, requires a client certificate and answers only the edge's (Caddy, CN
 * edge): any other certificate from the CA gets 403 before routing. The actuator port stays outside that allowlist
 * (Prometheus and the healthcheck present their own certificates). Test-only fixtures: src/test/resources/tls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("tls")
@Import(InMemoryLookups.class)
class GatewayEdgeOnlyTest {

    static final StubUpstream UPSTREAM = StubUpstream.start();

    private static Path tlsDir;

    @DynamicPropertySource
    static void certificatesAndRoutes(DynamicPropertyRegistry registry) throws IOException {
        tlsDir = TlsFixtures.installAs("api-gateway"); // the gateway's own identity (SAN localhost)
        registry.add("OJ_TLS_DIR", tlsDir::toString);
        registry.add("oj.gateway.submission-uri", UPSTREAM::baseUri);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        TlsFixtures.delete(tlsDir);
    }

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @BeforeEach
    void setUp() {
        UPSTREAM.reset();
    }

    @Test
    void theEdgeCertificateIsRouted() {
        TlsFixtures.webTestClientPresenting("edge", port).get().uri("/api/v1/languages").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:/api/v1/languages");
    }

    @Test
    void anyOtherCertificateFromTheCaGets403BeforeRouting() {
        TlsFixtures.webTestClientPresenting("tls-probe", port).get().uri("/api/v1/languages").exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.message").isEqualTo("Client certificate not allowed");
        assertThat(UPSTREAM.received()).isEmpty();
    }

    @Test
    void withoutAClientCertificateThereIsNoHttpAnswer() {
        // TLS 1.3 refuses after the handshake, on the first read: an alert, a reset or just the closed connection.
        Throwable refused = catchThrowable(() -> get(TlsFixtures.httpClientPresenting(null), port, "/api/v1/languages"));
        assertThat(refused).as("no HTTP answer without a client certificate").isInstanceOf(IOException.class);
        assertThat(UPSTREAM.received()).isEmpty();
    }

    @Test
    void theEdgesForwardedForReachesTheUpstreamFirst() {
        TlsFixtures.webTestClientPresenting("edge", port).get().uri("/api/v1/languages")
                .header("X-Forwarded-For", "203.0.113.7")
                .exchange()
                .expectStatus().isOk();
        assertThat(UPSTREAM.last().headers().getFirst("X-Forwarded-For")).startsWith("203.0.113.7");
    }

    @Test
    void thePublicHttpsOriginMayCallTheApi() {
        TlsFixtures.webTestClientPresenting("edge", port).options().uri("/api/v1/submissions")
                .header("Origin", "https://localhost")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://localhost");
    }

    @Test
    void theActuatorPortStaysOutsideTheEdgeAllowlist() throws Exception {
        HttpResponse<String> health = get(TlsFixtures.httpClientPresenting("tls-probe"), managementPort, "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"UP\"");
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
