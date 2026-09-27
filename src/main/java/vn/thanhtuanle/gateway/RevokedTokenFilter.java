package vn.thanhtuanle.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Strips a revoked access token from the request instead of rejecting it: the request then reaches
 * the service anonymously. Rejecting would also block {@code /auth/refresh}, which carries the same
 * revoked access cookie next to the refresh cookie — the user could never get a fresh token.
 * Public endpoints keep working, protected ones answer 401 and the portal refreshes. Fails open.
 */
@Component
class RevokedTokenFilter implements GlobalFilter, Ordered {

    static final String ACCESS_TOKEN_COOKIE = "accessToken";

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenFilter.class);

    private final RevocationLookup revocations;

    RevokedTokenFilter(RevocationLookup revocations) {
        this.revocations = revocations;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = accessToken(exchange.getRequest());
        Optional<UnverifiedClaims> claims = token == null ? Optional.empty() : UnverifiedClaims.parse(token);
        if (claims.isEmpty()) {
            return chain.filter(exchange);
        }
        return revocations.isRevoked(claims.get())
                .onErrorResume(e -> {
                    log.warn("Revocation lookup unavailable, keeping the token: {}", e.getMessage());
                    return Mono.just(false);
                })
                .flatMap(revoked -> chain.filter(revoked ? withoutAccessToken(exchange) : exchange));
    }

    static String accessToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        HttpCookie cookie = request.getCookies().getFirst(ACCESS_TOKEN_COOKIE);
        return cookie == null ? null : cookie.getValue();
    }

    static ServerWebExchange withoutAccessToken(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(HttpHeaders.AUTHORIZATION);
            List<String> kept = headers.getOrEmpty(HttpHeaders.COOKIE).stream()
                    .flatMap(header -> Arrays.stream(header.split(";")))
                    .map(String::trim)
                    .filter(pair -> !pair.isEmpty() && !pair.startsWith(ACCESS_TOKEN_COOKIE + "="))
                    .toList();
            headers.remove(HttpHeaders.COOKIE);
            if (!kept.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, String.join("; ", kept));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
