package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind the edge the gateway's socket peer is Caddy, which writes the browser's address as X-Forwarded-For and drops any
 * the browser sent. With {@code oj.gateway.edge.trust-forwarded-for} (the tls profile) the gateway keeps that header and
 * checks IP bans against its first hop, but only on a connection that presented the edge's certificate: any other
 * allow-listed caller is judged by its socket peer, and its X-Forwarded-For is discarded. Off (the default), a client's
 * X-Forwarded-For is always discarded and the socket peer is the client.
 */
class EdgeForwardedForTest {

    /** The tls profile (flag on), with a second allow-listed CN to prove the trust is the edge's alone. */
    @Nested
    @ActiveProfiles("tls")
    @TestPropertySource(properties = "oj.tls.allowed-clients=edge,tls-probe")
    class BehindTheEdge extends GatewayTestSupport {

        static Path tlsDir;

        @DynamicPropertySource
        static void gatewayIdentity(DynamicPropertyRegistry registry) throws IOException {
            tlsDir = TlsFixtures.installAs("api-gateway");
            registry.add("OJ_TLS_DIR", tlsDir::toString);
        }

        @AfterAll
        static void cleanUp() throws IOException {
            TlsFixtures.delete(tlsDir);
        }

        WebTestClient as(String identity) {
            return TlsFixtures.webTestClientPresenting(identity, port);
        }

        @Test
        void theUpstreamSeesTheEdgesFirstHopFirst() {
            as("edge").get().uri("/api/v1/languages").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();

            assertThat(UPSTREAM.last().headers().getFirst("X-Forwarded-For")).startsWith("203.0.113.7");
        }

        @Test
        void theOtherForwardingHeadersAreStillDropped() {
            as("edge").get().uri("/api/v1/languages")
                    .header("X-Forwarded-For", "203.0.113.7")
                    .header("X-Real-IP", "1.2.3.4")
                    .header("X-Forwarded-Host", "evil.example")
                    .header("Forwarded", "for=1.2.3.4")
                    .exchange()
                    .expectStatus().isOk();

            HttpHeaders seen = UPSTREAM.last().headers();
            assertThat(seen.containsKey("X-Real-IP")).isFalse();
            assertThat(String.valueOf(seen.get("X-Forwarded-Host"))).doesNotContain("evil.example");
            assertThat(String.valueOf(seen.get("Forwarded"))).doesNotContain("1.2.3.4");
        }

        @Test
        void aBanOnTheFirstHopIs403() {
            bans.banned.add("ip:203.0.113.7");

            as("edge").get().uri("/api/v1/problems").header("X-Forwarded-For", "203.0.113.7 ,10.0.0.9").exchange()
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.message").isEqualTo(AccessBanFilter.BANNED_MESSAGE);
            assertThat(PROBLEM.received()).isEmpty();
        }

        @Test
        void aBanOnTheSocketPeerDoesNotApplyWhenTheEdgeNamesTheClient() {
            bans.banned.add("ip:127.0.0.1"); // the edge's own address, as the gateway sees it

            as("edge").get().uri("/api/v1/problems").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();
        }

        @Test
        void withoutAForwardedForTheSocketPeerIsChecked() {
            bans.banned.add("ip:127.0.0.1");

            as("edge").get().uri("/api/v1/problems").exchange().expectStatus().isForbidden();
        }

        @Test
        void anotherAllowedCertificateCannotNameTheClient() {
            as("tls-probe").get().uri("/api/v1/languages").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();

            assertThat(UPSTREAM.last().headers().get("X-Forwarded-For")).containsExactly("127.0.0.1");
        }

        @Test
        void anotherAllowedCertificateIsBannedByItsSocketPeer() {
            bans.banned.add("ip:203.0.113.7");
            as("tls-probe").get().uri("/api/v1/problems").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();

            bans.banned.add("ip:127.0.0.1");
            as("tls-probe").get().uri("/api/v1/problems").header("X-Forwarded-For", "8.8.8.8").exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    class ByDefault extends GatewayTestSupport {

        @Test
        void aClientsForwardedForIsReplacedByTheSocketPeer() {
            client.get().uri("/api/v1/languages").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();

            assertThat(UPSTREAM.last().headers().get("X-Forwarded-For")).containsExactly("127.0.0.1");
        }

        @Test
        void aBanOnAClaimedForwardedForDoesNotApply() {
            bans.banned.add("ip:203.0.113.7");

            client.get().uri("/api/v1/problems").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();
        }
    }
}
