package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** The allowlist decides on the certificate alone: no TLS, no certificate, or another CN is a 403, whatever the path. */
class PeerCertificateWebFilterTest {

    private final PeerCertificateWebFilter filter = new PeerCertificateWebFilter(Set.of("edge"), new ObjectMapper());

    @Test
    void aRequestWithoutSslInfoIs403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build());

        assertThat(passes(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherCnIs403AndTheEdgePasses() throws Exception {
        MockServerWebExchange probe = presenting("tls-probe");
        MockServerWebExchange edge = presenting("edge");

        assertThat(passes(probe)).isFalse();
        assertThat(probe.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(passes(edge)).isTrue();
    }

    private static MockServerWebExchange presenting(String identity) throws Exception {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/api/v1/problems");
        request.sslInfo(EdgeClientIpTest.sslAs(identity));
        return MockServerWebExchange.from(request.build());
    }

    private boolean passes(MockServerWebExchange exchange) {
        AtomicBoolean reached = new AtomicBoolean();
        WebFilterChain chain = e -> {
            reached.set(true);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block(Duration.ofSeconds(5));
        return reached.get();
    }
}
