package vn.thanhtuanle.gateway;

import org.reactivestreams.Publisher;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** A real HTTP server standing in for an upstream service; it records every request it receives. */
final class StubUpstream {

    record Received(String method, String uri, HttpHeaders headers) {
    }

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final DisposableServer server;

    private StubUpstream() {
        this.server = HttpServer.create().host("127.0.0.1").port(0).handle(this::handle).bindNow();
    }

    static StubUpstream start() {
        return new StubUpstream();
    }

    String baseUri() {
        return "http://127.0.0.1:" + server.port();
    }

    List<Received> received() {
        return received;
    }

    Received last() {
        assertThat(received).as("requests that reached the upstream").isNotEmpty();
        return received.get(received.size() - 1);
    }

    void reset() {
        received.clear();
    }

    private Publisher<Void> handle(HttpServerRequest req, HttpServerResponse res) {
        HttpHeaders headers = new HttpHeaders();
        req.requestHeaders().forEach(e -> headers.add(e.getKey(), e.getValue()));
        received.add(new Received(req.method().name(), req.uri(), headers));
        return switch (URI.create(req.uri()).getPath()) {
            // "first" immediately, "second" only after 3s: lets a test tell streaming from buffering.
            case "/api/v1/submissions/s1/stream" -> res
                    .header("Content-Type", "text/event-stream")
                    .sendString(Flux.concat(
                            Flux.just("data: first\n\n"),
                            Mono.delay(Duration.ofSeconds(3)).thenMany(Flux.just("data: second\n\n"))));
            // Shape of the Google OAuth callback: redirect back to the SPA + auth cookie.
            case "/api/v1/auth/outbound/google/callback" -> res
                    .status(302)
                    .header("Location", "http://localhost/")
                    .header("Set-Cookie", "accessToken=abc; Path=/; HttpOnly")
                    .send();
            // An upstream that still adds its own CORS headers (an old service image).
            case "/api/v1/submissions/cors-from-upstream" -> res
                    .header("Access-Control-Allow-Origin", "http://localhost")
                    .header("Access-Control-Allow-Credentials", "true")
                    .sendString(Mono.just("ok"));
            // Echo how many body bytes arrived, to prove large uploads pass through intact.
            case "/api/v1/problems/import" -> res.sendString(
                    req.receive().aggregate().asByteArray().map(b -> "bytes:" + b.length));
            default -> res.header("Content-Type", "text/plain").sendString(Mono.just("upstream:" + req.uri()));
        };
    }
}
