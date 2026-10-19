package vn.thanhtuanle.gateway;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.netty.http.Http11SslContextSpec;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

/** The test-only certificates of src/test/resources/tls, as the gateway and its clients use them. */
final class TlsFixtures {

    private TlsFixtures() {
    }

    /** A temporary OJ_TLS_DIR as tls-init installs it: {@code identity}'s leaf as tls.crt/tls.key, plus ca.crt. */
    static Path installAs(String identity) throws IOException {
        Path dir = Files.createTempDirectory("oj-tls");
        for (Map.Entry<String, String> f : Map.of(identity + ".crt", "tls.crt", identity + ".key", "tls.key",
                "ca.crt", "ca.crt").entrySet()) {
            try (InputStream in = TlsFixtures.class.getResourceAsStream("/tls/" + f.getKey())) {
                if (in == null) {
                    throw new IllegalStateException("test fixture missing from the classpath: /tls/" + f.getKey()
                            + " (regenerate with judge-deployment/pki/pki.sh fixtures)");
                }
                Files.copy(in, dir.resolve(f.getValue()));
            }
        }
        return dir;
    }

    static void delete(Path dir) throws IOException {
        if (dir == null) {
            return;
        }
        try (var files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Trusts the test CA; presents the fixture {@code identity} (nothing when null). */
    static SslManagerBundle managers(String identity) {
        PemSslStoreDetails keyStore = identity == null ? null
                : new PemSslStoreDetails(null, "classpath:tls/" + identity + ".crt", "classpath:tls/" + identity + ".key");
        return SslBundle.of(new PemSslStoreBundle(keyStore, PemSslStoreDetails.forCertificate("classpath:tls/ca.crt")))
                .getManagers();
    }

    /** A JDK client over HTTP/1.1 that trusts the test CA and presents {@code identity} (nothing when null). */
    static HttpClient httpClientPresenting(String identity) throws Exception {
        SslManagerBundle managers = managers(identity);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), managers.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(context).version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** {@code https://localhost:<port>}, verified against the test CA, presenting {@code identity}. */
    static WebTestClient webTestClientPresenting(String identity, int port) {
        SslManagerBundle managers = managers(identity);
        Http11SslContextSpec tls = Http11SslContextSpec.forClient().configure(b -> b
                .trustManager(managers.getTrustManagerFactory())
                .keyManager(managers.getKeyManagerFactory()));
        reactor.netty.http.client.HttpClient http = reactor.netty.http.client.HttpClient.create()
                .secure(spec -> spec.sslContext(tls));
        return WebTestClient.bindToServer(new ReactorClientHttpConnector(http))
                .baseUrl("https://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }
}
