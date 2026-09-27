package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every other test overrides management.server.port with 0, so this one reads the shipped
 * application.yml directly: compose publishes only 8000, and keeping actuator on 8081 is what
 * makes /actuator unreachable from the host.
 */
class ManagementPortConfigTest {

    @Test
    void actuatorIsConfiguredOnTheUnpublishedPort8081() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        assertThat(String.valueOf(sources.get(0).getProperty("management.server.port"))).isEqualTo("8081");
    }
}
