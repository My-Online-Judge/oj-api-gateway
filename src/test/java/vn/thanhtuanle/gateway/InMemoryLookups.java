package vn.thanhtuanle.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stands in for Redis in gateway tests; each test sets exactly the bans/revocations it needs. */
@TestConfiguration(proxyBeanMethods = false)
class InMemoryLookups {

    static final class Bans implements BanLookup {
        final Set<String> banned = ConcurrentHashMap.newKeySet();
        volatile boolean unavailable;

        @Override
        public Mono<Boolean> isBanned(String type, String value) {
            if (unavailable) {
                return Mono.error(new IllegalStateException("ban store down"));
            }
            return Mono.just(value != null && banned.contains(type + ":" + value));
        }

        void reset() {
            banned.clear();
            unavailable = false;
        }
    }

    static final class Revocations implements RevocationLookup {
        final Set<String> revokedJtis = ConcurrentHashMap.newKeySet();
        volatile boolean unavailable;

        @Override
        public Mono<Boolean> isRevoked(UnverifiedClaims claims) {
            if (unavailable) {
                return Mono.error(new IllegalStateException("revocation store down"));
            }
            return Mono.just(claims.jti() != null && revokedJtis.contains(claims.jti()));
        }

        void reset() {
            revokedJtis.clear();
            unavailable = false;
        }
    }

    @Bean
    @Primary
    Bans testBans() {
        return new Bans();
    }

    @Bean
    @Primary
    Revocations testRevocations() {
        return new Revocations();
    }
}
