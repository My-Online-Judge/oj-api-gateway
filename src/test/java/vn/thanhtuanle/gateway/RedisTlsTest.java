package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import vn.thanhtuanle.oj.common.redis.RedisKeys;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis started the way docker-compose.yml starts it (TLS only, client certificate required). With the gateway's
 * own certificate in the "oj" SSL bundle the lookups work; without a bundle the lookup fails and the ban filter
 * fails open, counting it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "management.server.port=0",
        "spring.ssl.bundle.pem.oj.keystore.certificate=classpath:tls/api-gateway.crt",
        "spring.ssl.bundle.pem.oj.keystore.private-key=classpath:tls/api-gateway.key",
        "spring.ssl.bundle.pem.oj.truststore.certificate=classpath:tls/ca.crt",
        "spring.data.redis.ssl.bundle=oj",
        "spring.data.redis.timeout=2s",
        "spring.data.redis.connect-timeout=2s"})
class RedisTlsTest {

    private static final List<String> FIXTURES = List.of("server.crt", "server.key", "ca.crt");
    private static final GenericContainer<?> REDIS = startRedis();

    private static GenericContainer<?> startRedis() {
        // The server runs as uid 999 and must own its key, so the container installs the fixtures and then execs
        // the image entrypoint with the flags docker-compose.yml uses.
        GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379)
                .withCommand("sh", "-c", "install -d -m 755 /etc/oj && install -d -o redis -m 700 /etc/oj/tls"
                        + " && install -o redis -m 600 /tmp/fixtures/server.key /etc/oj/tls/tls.key"
                        + " && install -o redis -m 644 /tmp/fixtures/server.crt /etc/oj/tls/tls.crt"
                        + " && install -o redis -m 644 /tmp/fixtures/ca.crt /etc/oj/tls/ca.crt"
                        + " && exec docker-entrypoint.sh redis-server --port 0 --tls-port 6379"
                        + " --tls-cert-file /etc/oj/tls/tls.crt --tls-key-file /etc/oj/tls/tls.key"
                        + " --tls-ca-cert-file /etc/oj/tls/ca.crt --tls-auth-clients yes");
        for (String f : FIXTURES) {
            redis.withCopyFileToContainer(MountableFile.forClasspathResource("tls/" + f), "/tmp/fixtures/" + f);
        }
        try {
            redis.start();
        } catch (RuntimeException e) {
            redis.close();
            throw e;
        }
        return redis;
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stop() {
        REDIS.stop();
    }

    @Autowired
    BanLookup banLookup;
    @Autowired
    ReactiveStringRedisTemplate redis;

    @Test
    void aBanLookupAnswersOverMutualTls() {
        redis.opsForValue().set(RedisKeys.accessBan("IP", "9.9.9.9"), "1").block(Duration.ofSeconds(10));

        assertThat(banLookup.isBanned("ip", "9.9.9.9").block(Duration.ofSeconds(10))).isTrue();
        assertThat(banLookup.isBanned("ip", "8.8.8.8").block(Duration.ofSeconds(10))).isFalse();
    }

    @Test
    void aClientWithoutTlsCannotLookUpAndTheBanFilterFailsOpen() {
        LettuceConnectionFactory plain = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        plain.afterPropertiesSet();
        try {
            BanLookup lookup = new RedisLookups().banLookup(new ReactiveStringRedisTemplate(plain));
            StepVerifier.create(lookup.isBanned("ip", "9.9.9.9")).expectError().verify(Duration.ofSeconds(90));

            SimpleMeterRegistry meters = new SimpleMeterRegistry();
            AccessBanFilter filter = new AccessBanFilter(lookup, new ObjectMapper(), meters);
            GatewayFilterChain chain = exchange -> Mono.empty();
            filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/x").remoteAddress(new java.net.InetSocketAddress("9.9.9.9", 1234)).build()), chain)
                    .block(Duration.ofSeconds(90));

            assertThat(meters.get("oj.redis.fallback").tag("lookup", "ban").counter().count()).isEqualTo(1.0);
        } finally {
            plain.destroy();
        }
    }
}
