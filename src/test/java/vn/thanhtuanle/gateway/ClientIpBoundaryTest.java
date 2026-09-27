package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * judge-api's ClientIpResolver trusts the FIRST X-Forwarded-For hop, and its IP bans and per-IP
 * login rate limit key on that value. The gateway must therefore hand it exactly the socket peer.
 */
class ClientIpBoundaryTest extends GatewayTestSupport {

    @Test
    void spoofedForwardingHeadersAreReplacedByTheSocketPeer() {
        client.get().uri("/api/v1/languages")
                .header("X-Forwarded-For", "1.2.3.4")
                .header("X-Real-IP", "1.2.3.4")
                .header("Forwarded", "for=1.2.3.4")
                .exchange()
                .expectStatus().isOk();

        HttpHeaders seen = UPSTREAM.last().headers();
        assertThat(seen.get("X-Forwarded-For")).containsExactly("127.0.0.1");
        assertThat(seen.containsKey("X-Real-IP")).isFalse();
        assertThat(String.valueOf(seen.get("Forwarded"))).doesNotContain("1.2.3.4");
    }

    @Test
    void plainRequestGetsTheSocketPeerAsForwardedFor() {
        client.get().uri("/api/v1/languages").exchange().expectStatus().isOk();
        assertThat(UPSTREAM.last().headers().get("X-Forwarded-For")).containsExactly("127.0.0.1");
    }

    @Test
    void hostHeaderSentByTheBrowserIsPreserved() {
        client.get().uri("/api/v1/languages").exchange().expectStatus().isOk();
        assertThat(UPSTREAM.last().headers().getFirst(HttpHeaders.HOST)).isEqualTo("127.0.0.1:" + port);
    }
}
