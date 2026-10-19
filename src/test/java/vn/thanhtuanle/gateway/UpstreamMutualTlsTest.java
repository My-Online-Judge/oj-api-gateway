package vn.thanhtuanle.gateway;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.server.HttpServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Under the tls profile the gateway calls the services over mutual TLS: it presents the "oj" bundle's certificate (the
 * services require one) and accepts an upstream only if its certificate chains to the CA and names the host of the
 * route URI. Its actuator port serves TLS and requires a client certificate too. Test-only fixtures:
 * src/test/resources/tls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("tls")
@Import(InMemoryLookups.class)
class UpstreamMutualTlsTest {

    /** A service: the fixture server leaf (SAN localhost, 127.0.0.1), client certificate required. */
    static final MutualTlsUpstream SERVICE = MutualTlsUpstream.start("127.0.0.1", "server");

    /**
     * Every fixture leaf names localhost and 127.0.0.1 (pki.sh adds both to each SAN), so the wrong host is an
     * address no SAN lists: 127.0.0.2 (on Linux all of 127/8 is loopback). The certificate itself is valid and chains
     * to the CA.
     */
    static final MutualTlsUpstream WRONG_HOST = MutualTlsUpstream.start("127.0.0.2", "tls-probe");

    private static Path tlsDir;

    @DynamicPropertySource
    static void certificatesAndRoutes(DynamicPropertyRegistry registry) throws IOException {
        // The gateway's own identity, as tls-init installs it: tls.crt/tls.key + ca.crt in OJ_TLS_DIR.
        tlsDir = Files.createTempDirectory("oj-tls");
        for (Map.Entry<String, String> f : Map.of("api-gateway.crt", "tls.crt", "api-gateway.key", "tls.key",
                "ca.crt", "ca.crt").entrySet()) {
            try (InputStream in = UpstreamMutualTlsTest.class.getResourceAsStream("/tls/" + f.getKey())) {
                if (in == null) {
                    throw new IllegalStateException("test fixture missing from the classpath: /tls/" + f.getKey()
                            + " (regenerate with judge-deployment/pki/pki.sh fixtures)");
                }
                Files.copy(in, tlsDir.resolve(f.getValue()));
            }
        }
        registry.add("OJ_TLS_DIR", tlsDir::toString);
        registry.add("oj.gateway.submission-uri", () -> "https://localhost:" + SERVICE.port());
        registry.add("oj.gateway.problem-uri", () -> "https://127.0.0.2:" + WRONG_HOST.port());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        SERVICE.stop();
        WRONG_HOST.stop();
        try (var files = Files.walk(tlsDir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        // The gateway's own API port stays plain HTTP here (its server TLS is the edge's business).
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @Test
    void routesOverMutualTls() {
        client.get().uri("/api/v1/languages").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:/api/v1/languages");
        assertThat(SERVICE.clientSubject()).as("the certificate the gateway presented").isEqualTo("CN=api-gateway");
    }

    @Test
    void upstreamWithWrongHostnameFails() {
        int accepted = WRONG_HOST.accepted();

        client.get().uri("/api/v1/problems").exchange()
                .expectStatus().isEqualTo(502)
                .expectBody().jsonPath("$.status").isEqualTo(502);
        assertThat(WRONG_HOST.accepted()).as("connections the upstream accepted").isGreaterThan(accepted);
        assertThat(WRONG_HOST.requests()).as("requests sent over an unverified connection").isZero();
    }

    @Test
    void actuatorRequiresAClientCertificate() throws Exception {
        HttpResponse<String> health = get(clientPresenting("api-gateway"), "/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"UP\"");

        // TLS 1.3 reports a missing client certificate after the handshake, on the first read: depending on timing the
        // client sees the alert (SSLHandshakeException), a reset, or only the closed connection. The same port just
        // answered over TLS, so the client certificate is the one difference.
        Throwable refused = catchThrowable(() -> get(clientPresenting(null), "/actuator/health"));
        assertThat(refused).as("no HTTP answer without a client certificate").isInstanceOf(IOException.class);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + managementPort + path))
                .timeout(Duration.ofSeconds(10))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Trusts the test CA and presents the fixture {@code identity} (nothing when null). */
    private static HttpClient clientPresenting(String identity) throws Exception {
        SslManagerBundle managers = managers(identity);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), managers.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(context).version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private static SslManagerBundle managers(String identity) {
        PemSslStoreDetails keyStore = identity == null ? null
                : new PemSslStoreDetails(null, "classpath:tls/" + identity + ".crt",
                        "classpath:tls/" + identity + ".key");
        return SslBundle.of(new PemSslStoreBundle(keyStore, PemSslStoreDetails.forCertificate("classpath:tls/ca.crt")))
                .getManagers();
    }

    /**
     * A service stand-in: Reactor Netty over TLS with a fixture leaf, requiring a client certificate signed by the test
     * CA. Counts accepted connections (before TLS) and requests (after it), and records the client's subject.
     */
    static final class MutualTlsUpstream {
        private final DisposableServer server;
        private final AtomicInteger accepted = new AtomicInteger();
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicReference<String> clientSubject = new AtomicReference<>();

        private MutualTlsUpstream(String host, String leaf) {
            SslManagerBundle managers = managers(leaf);
            Http11SslContextSpec tls = Http11SslContextSpec.forServer(managers.getKeyManagerFactory())
                    .configure(b -> b.trustManager(managers.getTrustManagerFactory()).clientAuth(ClientAuth.REQUIRE));
            this.server = HttpServer.create().host(host).port(0)
                    .secure(spec -> spec.sslContext(tls))
                    .doOnChannelInit((observer, channel, remote) -> accepted.incrementAndGet())
                    .handle((req, res) -> {
                        requests.incrementAndGet();
                        req.withConnection(c ->
                                clientSubject.set(subjectOf(c.channel().pipeline().get(SslHandler.class))));
                        return res.header("Content-Type", "text/plain").sendString(Mono.just("upstream:" + req.uri()));
                    })
                    .bindNow();
        }

        static MutualTlsUpstream start(String host, String leaf) {
            return new MutualTlsUpstream(host, leaf);
        }

        private static String subjectOf(SslHandler tls) {
            try {
                return ((X509Certificate) tls.engine().getSession().getPeerCertificates()[0])
                        .getSubjectX500Principal().getName();
            } catch (SSLPeerUnverifiedException e) {
                return null;
            }
        }

        int port() {
            return server.port();
        }

        int accepted() {
            return accepted.get();
        }

        int requests() {
            return requests.get();
        }

        String clientSubject() {
            return clientSubject.get();
        }

        void stop() {
            server.disposeNow();
        }
    }
}
