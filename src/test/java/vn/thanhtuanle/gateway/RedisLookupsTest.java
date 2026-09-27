package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.thanhtuanle.oj.common.redis.RedisKeys;

import static org.assertj.core.api.Assertions.assertThat;

/** The Redis adapter against a real Redis, with the key formats judge-api writes. */
@Testcontainers(disabledWithoutDocker = true)
class RedisLookupsTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connections;
    private static ReactiveStringRedisTemplate redis;
    private final RedisLookups lookups = new RedisLookups();

    @BeforeAll
    static void connect() {
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connections.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(connections);
    }

    @AfterAll
    static void disconnect() {
        connections.destroy();
    }

    @BeforeEach
    void flush() {
        redis.execute(connection -> connection.serverCommands().flushAll()).blockLast();
    }

    @Test
    void aMirroredBanIsFound() {
        redis.opsForValue().set(RedisKeys.accessBan("DEVICE", "d1"), "1").block();

        assertThat(lookups.banLookup(redis).isBanned("device", "d1").block()).isTrue();
        assertThat(lookups.banLookup(redis).isBanned("device", "d2").block()).isFalse();
        assertThat(lookups.banLookup(redis).isBanned("device", null).block()).isFalse();
    }
}
