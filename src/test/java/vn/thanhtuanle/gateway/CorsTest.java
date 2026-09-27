package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsTest extends GatewayTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://localhost:5173"})
    void preflightFromThePortalIsAnsweredByTheGatewayAlone(String origin) {
        client.options().uri("/api/v1/submissions")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", origin)
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
        assertThat(UPSTREAM.received()).isEmpty();
    }

    @Test
    void preflightFromAnUnknownOriginIsRejected() {
        client.options().uri("/api/v1/submissions")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
        assertThat(UPSTREAM.received()).isEmpty();
    }

    @Test
    void actualRequestCarriesExactlyOneAllowOriginEvenWhenTheUpstreamAddsOne() {
        client.get().uri("/api/v1/cors-from-upstream")
                .header("Origin", "http://localhost")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values("Access-Control-Allow-Origin",
                        v -> assertThat(v).containsExactly("http://localhost"))
                .expectHeader().values("Access-Control-Allow-Credentials",
                        v -> assertThat(v).containsExactly("true"));
    }
}
