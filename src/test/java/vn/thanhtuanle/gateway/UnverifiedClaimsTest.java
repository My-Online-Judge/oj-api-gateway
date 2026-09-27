package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnverifiedClaimsTest {

    private static String jwt(String payloadJson) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    @Test
    void readsJtiUidAndIat() {
        UnverifiedClaims claims = UnverifiedClaims.parse(
                jwt("{\"jti\":\"j1\",\"uid\":\"11111111-2222-3333-4444-555555555555\",\"iat\":1700000000}")).orElseThrow();

        assertThat(claims.jti()).isEqualTo("j1");
        assertThat(claims.userId()).isEqualTo(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        assertThat(claims.issuedAt()).isEqualTo(1700000000L);
    }

    @Test
    void anOldFormatTokenHasNoUserId() {
        UnverifiedClaims claims = UnverifiedClaims.parse(jwt("{\"jti\":\"j1\",\"iat\":1}")).orElseThrow();

        assertThat(claims.userId()).isNull();
    }

    @Test
    void aMalformedUidIsIgnoredRatherThanFatal() {
        assertThat(UnverifiedClaims.parse(jwt("{\"uid\":\"not-a-uuid\"}")).orElseThrow().userId()).isNull();
    }

    @Test
    void notAJwtParsesToNothing() {
        assertThat(UnverifiedClaims.parse("not-a-jwt")).isEmpty();
        assertThat(UnverifiedClaims.parse("a.%%%.c")).isEmpty();
    }
}
