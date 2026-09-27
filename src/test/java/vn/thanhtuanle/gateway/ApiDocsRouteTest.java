package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

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
}
