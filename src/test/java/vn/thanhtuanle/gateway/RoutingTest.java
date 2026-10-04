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
    void apiPathIsForwardedToSubmissionServiceUnchanged() {
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
        assertThat(IDENTITY.received()).isEmpty();
        assertThat(PROBLEM.received()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/me", "/api/v1/auth/login", "/api/v1/users", "/api/v1/users/42/roles",
            "/api/v1/roles", "/api/v1/permissions", "/api/v1/security/bans?page=0"})
    void identityPathsGoToIdentityService(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:" + path);
        assertThat(UPSTREAM.received()).as("submission-service sees none of them").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/problems", "/api/v1/problems?page=0&size=10&status=ACTIVE",
            "/api/v1/problems/a-plus-b", "/api/v1/problems/a-plus-b/test-cases"})
    void problemPathsGoToProblemService(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:" + path);
        assertThat(UPSTREAM.received()).as("submission-service sees none of them").isEmpty();
        assertThat(PROBLEM.received()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/submissions", "/api/v1/submissions/user/42", "/api/v1/submissions/s1",
            "/api/v1/languages", "/api/v1/judge-servers"})
    void submissionPathsGoToSubmissionService(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("upstream:" + path);
        assertThat(UPSTREAM.received()).hasSize(1);
        assertThat(IDENTITY.received()).as("identity-service sees none of them").isEmpty();
        assertThat(PROBLEM.received()).as("problem-service sees none of them").isEmpty();
    }

    // Since sub-project 3a no service owns "the rest": an unclaimed API path is the gateway's own 404.
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/foo", "/api/v1/usersx", "/api/v1/problemsx", "/api/v1/submissionsx"})
    void unclaimedApiPathsAreA404FromTheGateway(String path) {
        client.get().uri(path).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
        assertThat(UPSTREAM.received()).isEmpty();
        assertThat(IDENTITY.received()).isEmpty();
        assertThat(PROBLEM.received()).isEmpty();
    }

    @Test
    void oauthRedirectAndAuthCookieFromIdentityServiceReachTheBrowser() {
        client.get().uri("/api/v1/auth/outbound/google/callback?code=c&state=s").exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost/")
                .expectHeader().valueEquals("Set-Cookie", "accessToken=abc; Path=/; HttpOnly");
    }

    @Test
    void authCookieFromTheBrowserReachesIdentityService() {
        client.get().uri("/api/v1/auth/me").cookie("accessToken", "tok").exchange().expectStatus().isOk();
        assertThat(IDENTITY.last().headers().getFirst("Cookie")).contains("accessToken=tok");
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
        assertThat(PROBLEM.received()).as("problem imports go to problem-service").hasSize(1);
    }
}
