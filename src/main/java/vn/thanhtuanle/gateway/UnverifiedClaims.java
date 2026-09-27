package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * The claims the gateway needs from an access token, read WITHOUT verifying its signature. Safe
 * because the gateway only uses them to take access away: a forged token that gets past this is
 * still rejected by the service's signature check, and a genuine revoked token's claims are real.
 *
 * @param issuedAt {@code iat} in epoch seconds, 0 when absent
 */
public record UnverifiedClaims(String jti, UUID userId, long issuedAt) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Empty when the value is not a three-part JWT with a JSON payload. */
    static Optional<UnverifiedClaims> parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            return Optional.of(new UnverifiedClaims(
                    text(payload, "jti"), uuid(text(payload, "uid")), payload.path("iat").asLong(0)));
        } catch (IllegalArgumentException | IOException e) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
