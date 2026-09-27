package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "oj.gateway.expose-api-docs=true")
class ApiDocsRouteTest extends GatewayTestSupport {

    @Test
    void openApiDocsAreForwardedWhenEnabled() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:/v3/api-docs");
    }

    @Test
    void swaggerUiIsForwardedWhenEnabled() {
        client.get().uri("/swagger-ui/index.html").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:/swagger-ui/index.html");
    }

    @Test
    void edgeRulesApplyToThisRouteToo() {
        // Guards the reason EdgeHeaders is global: every route, not only monolith-api, must strip
        // client forwarding headers and keep the browser's Host.
        client.get().uri("/v3/api-docs").header("X-Forwarded-For", "1.2.3.4").exchange()
                .expectStatus().isOk();
        assertThat(UPSTREAM.last().headers().get("X-Forwarded-For")).containsExactly("127.0.0.1");
        assertThat(UPSTREAM.last().headers().getFirst("Host")).isEqualTo("127.0.0.1:" + port);
    }
}
