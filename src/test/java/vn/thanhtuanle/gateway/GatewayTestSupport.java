package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Boots the real gateway on a random port in front of two {@link StubUpstream}s — the monolith and
 * identity-service. The client connects
 * to 127.0.0.1 (not "localhost", which may resolve to ::1) so the socket peer is predictable.
 * Bans and revocations come from {@link InMemoryLookups}, empty unless a test adds some.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(InMemoryLookups.class)
abstract class GatewayTestSupport {

    static final StubUpstream UPSTREAM = StubUpstream.start();
    static final StubUpstream IDENTITY = StubUpstream.start();
    static final StubUpstream PROBLEM = StubUpstream.start();

    @DynamicPropertySource
    static void routeToStub(DynamicPropertyRegistry registry) {
        registry.add("oj.gateway.monolith-uri", UPSTREAM::baseUri);
        registry.add("oj.gateway.identity-uri", IDENTITY::baseUri);
        registry.add("oj.gateway.problem-uri", PROBLEM::baseUri);
    }

    @LocalServerPort
    int port;

    @Autowired
    InMemoryLookups.Bans bans;

    @Autowired
    InMemoryLookups.Revocations revocations;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        UPSTREAM.reset();
        IDENTITY.reset();
        PROBLEM.reset();
        bans.reset();
        revocations.reset();
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }
}
