package vn.thanhtuanle.gateway;

import reactor.core.publisher.Mono;

/** Whether an access token has been revoked. */
public interface RevocationLookup {

    /**
     * True when the token was logged out (its jti) or issued before its user's revocation cutoff. The cutoff is
     * compared in milliseconds when the token carries {@code iat_ms} and identity wrote the millisecond marker,
     * otherwise in whole seconds (see {@code RevocationCutoffs}), so a token is never revoked less than before.
     */
    Mono<Boolean> isRevoked(UnverifiedClaims claims);
}
