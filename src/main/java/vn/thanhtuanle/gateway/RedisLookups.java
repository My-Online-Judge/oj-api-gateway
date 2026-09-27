package vn.thanhtuanle.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import vn.thanhtuanle.oj.common.redis.RedisKeys;

/** Ban lookup against the Redis keys judge-api writes (formats from oj-common). */
@Configuration(proxyBeanMethods = false)
class RedisLookups {

    @Bean
    BanLookup banLookup(ReactiveStringRedisTemplate redis) {
        return (type, value) -> value == null || value.isBlank()
                ? Mono.just(false)
                : redis.hasKey(RedisKeys.accessBan(type, value));
    }
}
