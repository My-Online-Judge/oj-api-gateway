package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reactor Netty keeps idle pooled upstream connections forever by default, while the services' Tomcat
 * closes idle keep-alive connections after its keep-alive timeout (60s unless configured). Reusing a
 * connection Tomcat is closing at that instant fails with PrematureCloseException — a sporadic,
 * unreproducible error for users. The gateway must retire idle connections first.
 */
class HttpClientPoolConfigTest {

    private static final Duration TOMCAT_DEFAULT_KEEP_ALIVE = Duration.ofSeconds(60);

    @Test
    void idlePooledConnectionsAreRetiredBeforeTheUpstreamClosesThem() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        Object maxIdle = sources.get(0).getProperty("spring.cloud.gateway.server.webflux.httpclient.pool.max-idle-time");

        assertThat(maxIdle).as("httpclient.pool.max-idle-time").isNotNull();
        assertThat(DurationStyle.detectAndParse(String.valueOf(maxIdle))).isLessThan(TOMCAT_DEFAULT_KEEP_ALIVE);
    }
}
