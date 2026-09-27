package vn.thanhtuanle.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import vn.thanhtuanle.oj.common.redis.RedisKeys;

/** Ban and revocation lookups against the Redis keys judge-api writes (formats from oj-common). */
@Configuration(proxyBeanMethods = false)
class RedisLookups {

    @Bean
    BanLookup banLookup(ReactiveStringRedisTemplate redis) {
        return (type, value) -> value == null || value.isBlank()
                ? Mono.just(false)
                : redis.hasKey(RedisKeys.accessBan(type, value));
    }

    @Bean
    RevocationLookup revocationLookup(ReactiveStringRedisTemplate redis) {
        return claims -> {
            Mono<Boolean> loggedOut = claims.jti() == null
                    ? Mono.just(false)
                    : redis.hasKey(RedisKeys.tokenBlocklist(claims.jti()));
            Mono<Boolean> issuedBeforeCutoff = claims.userId() == null
                    ? Mono.just(false)
                    : redis.opsForValue().get(RedisKeys.tokenRevokedBefore(claims.userId()))
                            .map(cutoff -> claims.issuedAt() < Long.parseLong(cutoff))
                            .defaultIfEmpty(false);
            return loggedOut.flatMap(revoked -> revoked ? Mono.just(true) : issuedBeforeCutoff);
        };
    }
}
