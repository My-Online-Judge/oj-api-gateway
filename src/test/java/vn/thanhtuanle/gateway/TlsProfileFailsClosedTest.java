package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** With the tls profile and no certificate files the gateway must fail at startup, never fall back to plaintext. */
class TlsProfileFailsClosedTest {

    @Test
    void missingCertificateFilesFailTheStartup() {
        ConfigurableApplicationContext started = null;
        Throwable failure = null;
        try {
            started = new SpringApplicationBuilder(ApiGatewayApplication.class)
                    .run("--spring.profiles.active=tls", "--OJ_TLS_DIR=/nonexistent", "--server.port=0",
                            "--management.server.port=0");
        } catch (Throwable e) {
            failure = e;
        } finally {
            if (started != null) {
                started.close();
            }
        }
        assertThat(failure).as("the tls profile with no certificate files must not start").isNotNull();
        StringBuilder chain = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            chain.append(t.getMessage()).append('\n');
        }
        assertThat(chain.toString()).contains("/nonexistent/tls.crt");
    }
}
