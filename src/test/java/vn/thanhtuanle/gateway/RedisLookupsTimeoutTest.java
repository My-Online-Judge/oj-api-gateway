package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Redis that stops answering must cost a request milliseconds, not Lettuce's 60 s default: the
 * checks fail open, but only a fast failure keeps requests flowing. Runs the production lookups
 * with the gateway's own Redis settings against a stand-in that accepts connections and never
 * replies, like a hung Redis.
 */
@SpringBootTest(properties = "management.server.port=0")
class RedisLookupsTimeoutTest {

    private static final Duration GIVE_UP_WITHIN = Duration.ofSeconds(1);
    private static final SilentServer SILENT_REDIS = SilentServer.start();

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", SILENT_REDIS::port);
    }

    @AfterAll
    static void stop() {
        SILENT_REDIS.close();
    }

    @Autowired
    BanLookup banLookup;
    @Autowired
    RevocationLookup revocationLookup;

    @Test
    void aSilentRedisFailsTheBanLookupFast() {
        long start = System.nanoTime();

        StepVerifier.create(banLookup.isBanned("ip", "1.2.3.4")).expectError().verify(Duration.ofMinutes(3));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(GIVE_UP_WITHIN);
    }

    @Test
    void aSilentRedisFailsTheRevocationLookupFast() {
        UnverifiedClaims claims = new UnverifiedClaims("jti-1", UUID.randomUUID(), 1000);
        long start = System.nanoTime();

        StepVerifier.create(revocationLookup.isRevoked(claims)).expectError().verify(Duration.ofMinutes(3));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(GIVE_UP_WITHIN);
    }

    /** Accepts TCP connections and never writes a byte. */
    static final class SilentServer {
        private final ServerSocket socket;
        private final List<Socket> accepted = new CopyOnWriteArrayList<>();

        private SilentServer(ServerSocket socket) {
            this.socket = socket;
        }

        static SilentServer start() {
            try {
                SilentServer server = new SilentServer(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()));
                Thread acceptor = new Thread(server::acceptForever, "silent-redis");
                acceptor.setDaemon(true);
                acceptor.start();
                return server;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        int port() {
            return socket.getLocalPort();
        }

        private void acceptForever() {
            while (!socket.isClosed()) {
                try {
                    accepted.add(socket.accept());
                } catch (IOException closed) {
                    return;
                }
            }
        }

        void close() {
            try {
                socket.close();
                for (Socket connection : accepted) {
                    connection.close();
                }
            } catch (IOException ignored) {
                // test teardown
            }
        }
    }
}
