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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The Redis adapters against a real Redis, with the key formats judge-api writes. */
@Testcontainers(disabledWithoutDocker = true)
class RedisLookupsTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");

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

    private boolean revoked(String jti, long issuedAt) {
        return lookups.revocationLookup(redis).isRevoked(new UnverifiedClaims(jti, ALICE, issuedAt)).block();
    }

    @Test
    void aMirroredBanIsFound() {
        redis.opsForValue().set(RedisKeys.accessBan("DEVICE", "d1"), "1").block();

        assertThat(lookups.banLookup(redis).isBanned("device", "d1").block()).isTrue();
        assertThat(lookups.banLookup(redis).isBanned("device", "d2").block()).isFalse();
        assertThat(lookups.banLookup(redis).isBanned("device", null).block()).isFalse();
    }

    @Test
    void aLoggedOutJtiIsRevoked() {
        redis.opsForValue().set(RedisKeys.tokenBlocklist("j1"), "1").block();

        assertThat(revoked("j1", 1000)).isTrue();
        assertThat(revoked("j2", 1000)).isFalse();
    }

    @Test
    void tokensIssuedBeforeTheUsersCutoffAreRevoked_laterOnesAreNot() {
        redis.opsForValue().set(RedisKeys.tokenRevokedBefore(ALICE), "1000").block();

        assertThat(revoked("j", 999)).as("issued before the cutoff").isTrue();
        assertThat(revoked("j", 1000)).as("re-issued by the refresh right after it").isFalse();
    }

    @Test
    void noCutoffMeansNotRevoked() {
        assertThat(revoked("j", 1)).isFalse();
    }
}
