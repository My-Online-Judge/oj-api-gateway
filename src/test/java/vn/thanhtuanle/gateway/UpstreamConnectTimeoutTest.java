package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A stopped upstream fails at name resolution (UpstreamDnsConfig); the timeout bounds one that resolves but is silent. */
class UpstreamConnectTimeoutTest {

    @Test
    void anUpstreamThatDoesNotAnswerFailsWithinHalfASecond() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        Object timeout = sources.get(0).getProperty("spring.cloud.gateway.server.webflux.httpclient.connect-timeout");

        assertThat(timeout).as("httpclient.connect-timeout, ms").isNotNull();
        assertThat(Integer.parseInt(String.valueOf(timeout))).isPositive().isLessThanOrEqualTo(500);
    }
}
