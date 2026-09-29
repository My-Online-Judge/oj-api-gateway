package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class RevokedTokenFilterTest extends GatewayTestSupport {

    /** JWT-shaped: the gateway reads the payload without verifying, so the signature is a dummy. */
    private static String token(String jti) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String payload = "{\"jti\":\"" + jti + "\",\"uid\":\"11111111-2222-3333-4444-555555555555\",\"iat\":1700000000}";
        return b64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    @Test
    void aRevokedBearerTokenIsStrippedAndTheRequestStillGoesThrough() {
        revocations.revokedJtis.add("jti-revoked");

        client.get().uri("/api/v1/submissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("jti-revoked"))
                .exchange().expectStatus().isOk();

        assertThat(UPSTREAM.last().headers().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void aRevokedCookieIsStrippedWhileOtherCookiesSurvive() {
        revocations.revokedJtis.add("jti-revoked");

        // The /auth/refresh case: the revoked access cookie travels with the refresh cookie.
        client.post().uri("/api/v1/auth/refresh")
                .cookie("theme", "dark").cookie("accessToken", token("jti-revoked")).cookie("refreshToken", "r")
                .exchange().expectStatus().isOk();

        String cookies = IDENTITY.last().headers().getFirst(HttpHeaders.COOKIE);
        assertThat(cookies).contains("theme=dark").contains("refreshToken=r").doesNotContain("accessToken");
    }

    @Test
    void aValidTokenIsForwardedUntouched() {
        String token = token("jti-fine");

        client.get().uri("/api/v1/submissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();

        assertThat(UPSTREAM.last().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    @Test
    void anUnparsableTokenIsLeftForTheServiceToReject() {
        client.get().uri("/api/v1/submissions").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange().expectStatus().isOk();

        assertThat(UPSTREAM.last().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer not-a-jwt");
    }

    @Test
    void anUnreachableRevocationStoreFailsOpen() {
        revocations.revokedJtis.add("jti-revoked");
        revocations.unavailable = true;

        client.get().uri("/api/v1/submissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("jti-revoked"))
                .exchange().expectStatus().isOk();

        assertThat(UPSTREAM.last().headers().containsKey(HttpHeaders.AUTHORIZATION)).isTrue();
    }
}
