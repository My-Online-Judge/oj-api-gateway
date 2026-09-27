package vn.thanhtuanle.gateway;

import reactor.core.publisher.Mono;

/** Whether an access token has been revoked. */
public interface RevocationLookup {

    /** True when the token was logged out (its jti) or issued before its user's revocation cutoff. */
    Mono<Boolean> isRevoked(UnverifiedClaims claims);
}
