package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X-Forwarded-For is the client only when the flag is on AND the caller presented the edge's certificate: another
 * allow-listed CN (or a request without TLS) cannot name an address to dodge an IP ban.
 */
class EdgeClientIpTest {

    private static final InetSocketAddress PEER = new InetSocketAddress("198.51.100.9", 40000);

    @Test
    void theEdgeNamesTheClientInItsFirstHop() throws Exception {
        assertThat(EdgeClientIp.resolve(exchange(sslAs("edge"), "203.0.113.7 , 10.0.0.1"), true)).isEqualTo("203.0.113.7");
    }

    @Test
    void anotherCertificateCannotNameTheClient() throws Exception {
        assertThat(EdgeClientIp.resolve(exchange(sslAs("tls-probe"), "203.0.113.7"), true)).isEqualTo("198.51.100.9");
    }

    @Test
    void withoutTlsTheSocketPeerIsTheClient() {
        assertThat(EdgeClientIp.resolve(exchange(null, "203.0.113.7"), true)).isEqualTo("198.51.100.9");
    }

    @Test
    void withTheFlagOffEvenTheEdgeCannotNameTheClient() throws Exception {
        assertThat(EdgeClientIp.resolve(exchange(sslAs("edge"), "203.0.113.7"), false)).isEqualTo("198.51.100.9");
    }

    @Test
    void theEdgeWithoutAForwardedForIsTheSocketPeer() throws Exception {
        assertThat(EdgeClientIp.resolve(exchange(sslAs("edge"), null), true)).isEqualTo("198.51.100.9");
    }

    private static MockServerWebExchange exchange(SslInfo ssl, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/v1/problems").remoteAddress(PEER);
        if (ssl != null) {
            request.sslInfo(ssl);
        }
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return MockServerWebExchange.from(request.build());
    }

    static SslInfo sslAs(String identity) throws Exception {
        X509Certificate[] peers = {TlsFixtures.certificate(identity)};
        return new SslInfo() {
            @Override
            public String getSessionId() {
                return "test";
            }

            @Override
            public X509Certificate[] getPeerCertificates() {
                return peers;
            }
        };
    }
}
