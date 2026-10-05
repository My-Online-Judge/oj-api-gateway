package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The upstream accepts the connection, reads the request, then closes the socket without
 * answering: a service restarting mid-request, or a pooled keep-alive connection it closed just as
 * the gateway reused it. That is a downstream connection failure (spec §8) → 503, not a 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class UpstreamClosesConnectionTest {

    static final ServerSocket CLOSING_UPSTREAM = startClosingUpstream();

    static ServerSocket startClosingUpstream() {
        try {
            ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket connection = server.accept()) {
                        connection.getInputStream().read(new byte[8192]); // take the request, answer nothing
                    } catch (IOException ignored) {
                        // closing the socket is the whole point
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void routeToTheClosingUpstream(DynamicPropertyRegistry registry) {
        registry.add("oj.gateway.submission-uri", () -> "http://127.0.0.1:" + CLOSING_UPSTREAM.getLocalPort());
    }

    @LocalServerPort
    int port;

    @Test
    void connectionClosedBeforeAnyResponseReturns503() {
        WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build()
                .get().uri("/api/v1/languages").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.message").isEqualTo("Service temporarily unavailable");
    }
}
