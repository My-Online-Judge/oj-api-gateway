package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingTest extends GatewayTestSupport {

    @Test
    void apiPathIsForwardedToTheMonolithUnchanged() {
        client.get().uri("/api/v1/languages?page=0").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:/api/v1/languages?page=0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/judge_server_heartbeat", "/actuator/health", "/v3/api-docs", "/swagger-ui/index.html", "/"})
    void pathsOutsideThePublicApiAreNotRouted(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
        assertThat(UPSTREAM.received()).isEmpty();
    }

    @Test
    void oauthRedirectAndAuthCookieFromTheMonolithReachTheBrowser() {
        client.get().uri("/api/v1/auth/outbound/google/callback?code=c&state=s").exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost/")
                .expectHeader().valueEquals("Set-Cookie", "accessToken=abc; Path=/; HttpOnly");
    }

    @Test
    void authCookieFromTheBrowserReachesTheMonolith() {
        client.get().uri("/api/v1/auth/me").cookie("accessToken", "tok").exchange().expectStatus().isOk();
        assertThat(UPSTREAM.last().headers().getFirst("Cookie")).contains("accessToken=tok");
    }

    @Test
    void sseEventsAreStreamedAsTheyArriveNotBuffered() {
        Flux<String> events = client.get().uri("/api/v1/submissions/s1/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        // The upstream sends "second" only after 3s. A buffering proxy would deliver nothing before
        // then, so receiving "first" within 1.5s proves events are streamed through.
        StepVerifier.create(events).expectNext("first").thenCancel().verify(Duration.ofMillis(1500));
    }

    @Test
    void largeUploadIsForwardedIntact() {
        byte[] eightMegabytes = new byte[8 * 1024 * 1024];
        client.post().uri("/api/v1/problems/import")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(eightMegabytes)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("bytes:" + eightMegabytes.length);
    }
}
