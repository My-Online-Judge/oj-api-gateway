package vn.thanhtuanle.gateway;

import io.lettuce.core.ClientOptions;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** oj-common's Redis settings reach the gateway's client (sub-project 6 §3.2 B). */
class RedisClientOptionsTest extends GatewayTestSupport {

    @Autowired
    LettuceConnectionFactory redis;

    @Test
    void aCommandSentWhileRedisIsDisconnectedFailsAtOnce() {
        assertThat(redis.getClientConfiguration().getClientOptions().orElseThrow().getDisconnectedBehavior())
                .isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    @Test
    void redisIsResolvedByTheJdkNotByNettysCachingResolver() {
        // reactor-netty puts netty-resolver-dns on this classpath; without oj-common's setting Lettuce would use it.
        assertThat(redis.getClientConfiguration().getClientResources().orElseThrow().addressResolverGroup())
                .isSameAs(DefaultAddressResolverGroup.INSTANCE);
    }
}
