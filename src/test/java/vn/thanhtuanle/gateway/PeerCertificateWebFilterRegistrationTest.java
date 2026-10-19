package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edge allowlist is what keeps every other CA-signed certificate (tls-probe, a service's) off the API port: when
 * the server asks for client certificates, an empty or blank-only {@code oj.tls.allowed-clients} fails the startup.
 */
class PeerCertificateWebFilterRegistrationTest {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withUserConfiguration(PeerCertificateWebFilter.Registration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @ParameterizedTest
    @ValueSource(strings = {"need", "want"})
    void clientCertificatesWithoutAnAllowlistFailTheStartup(String clientAuth) {
        runner.withPropertyValues("server.ssl.client-auth=" + clientAuth)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("oj.tls.allowed-clients");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"oj.tls.allowed-clients= ", "oj.tls.allowed-clients[0]="})
    void aBlankOnlyAllowlistIsNoAllowlist(String property) {
        int eq = property.indexOf('=');
        runner.withPropertyValues("server.ssl.client-auth=need")
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "blank", Map.of(property.substring(0, eq), property.substring(eq + 1)))))
                .run(ctx -> assertThat(ctx.getStartupFailure()).rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("oj.tls.allowed-clients"));
    }

    @Test
    void clientCertificatesWithTheEdgeListedGetTheFilter() {
        runner.withPropertyValues("server.ssl.client-auth=need", "oj.tls.allowed-clients=edge")
                .run(ctx -> assertThat(ctx).hasSingleBean(PeerCertificateWebFilter.class));
    }

    @Test
    void withoutClientCertificatesOrAnAllowlistThereIsNoFilter() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(PeerCertificateWebFilter.class);
        });
    }
}
