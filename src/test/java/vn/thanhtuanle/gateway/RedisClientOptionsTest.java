package vn.thanhtuanle.gateway;

import io.lettuce.core.ClientOptions;
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
}
