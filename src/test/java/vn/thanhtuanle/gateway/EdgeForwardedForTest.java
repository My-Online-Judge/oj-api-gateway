package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind the edge the gateway's socket peer is Caddy, which writes the browser's address as X-Forwarded-For and drops any
 * the browser sent. With {@code oj.gateway.edge.trust-forwarded-for} (the tls profile, where port 8000 answers only the
 * edge's certificate) the gateway keeps that header and checks IP bans against its first hop. Off (the default), a
 * client's X-Forwarded-For is discarded and the socket peer is the client.
 */
class EdgeForwardedForTest {

    @Nested
    @TestPropertySource(properties = "oj.gateway.edge.trust-forwarded-for=true")
    class BehindTheEdge extends GatewayTestSupport {

        @Test
        void theUpstreamSeesTheEdgesFirstHopFirst() {
            client.get().uri("/api/v1/languages").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();

            assertThat(UPSTREAM.last().headers().getFirst("X-Forwarded-For")).startsWith("203.0.113.7");
        }

        @Test
        void theOtherForwardingHeadersAreStillDropped() {
            client.get().uri("/api/v1/languages")
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

            client.get().uri("/api/v1/problems").header("X-Forwarded-For", "203.0.113.7 ,10.0.0.9").exchange()
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.message").isEqualTo(AccessBanFilter.BANNED_MESSAGE);
            assertThat(PROBLEM.received()).isEmpty();
        }

        @Test
        void aBanOnTheSocketPeerDoesNotApplyWhenTheEdgeNamesTheClient() {
            bans.banned.add("ip:127.0.0.1"); // the edge's own address, as the gateway sees it

            client.get().uri("/api/v1/problems").header("X-Forwarded-For", "203.0.113.7").exchange()
                    .expectStatus().isOk();
        }

        @Test
        void withoutAForwardedForTheSocketPeerIsChecked() {
            bans.banned.add("ip:127.0.0.1");

            client.get().uri("/api/v1/problems").exchange().expectStatus().isForbidden();
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
