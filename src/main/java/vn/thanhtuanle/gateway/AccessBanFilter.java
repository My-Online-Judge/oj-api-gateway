package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.thanhtuanle.oj.common.client.ClientFingerprint;

/**
 * Refuses requests from a banned IP (the socket peer — EdgeHeaders guarantees no client can spoof
 * it) or a banned device ({@link ClientFingerprint}, the rule the bans were recorded with). Fails
 * open: when the ban store is unreachable the request goes through, as it did in judge-api.
 */
@Component
class AccessBanFilter implements GlobalFilter, Ordered {

    static final String BANNED_MESSAGE = "Access from this address or device is banned";

    private static final Logger log = LoggerFactory.getLogger(AccessBanFilter.class);

    private final BanLookup bans;
    private final ObjectMapper objectMapper;
    private final Counter banned;
    private final Counter fallbacks;

    AccessBanFilter(BanLookup bans, ObjectMapper objectMapper, MeterRegistry registry) {
        this.bans = bans;
        this.objectMapper = objectMapper;
        // Same meter judge-api registered, so the BannedRequestFlood alert keeps working unchanged.
        this.banned = Counter.builder("oj.request.banned")
                .description("Requests refused because the client IP or device is banned")
                .register(registry);
        // Sub-project 6: the RedisUnavailable rule watches every request let through because Redis did not answer.
        this.fallbacks = Counter.builder("oj.redis.fallback")
                .description("Requests or work that went on without Redis because a Redis call failed (fail-open)")
                .tag("lookup", "ban")
                .register(registry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String ip = request.getRemoteAddress() == null ? null : request.getRemoteAddress().getHostString();
        String device = ClientFingerprint.deviceHash(
                request.getHeaders().getFirst("X-Device-Id"), request.getHeaders().getFirst(HttpHeaders.USER_AGENT));
        return bans.isBanned("ip", ip)
                .flatMap(ipBanned -> ipBanned ? Mono.just(true) : bans.isBanned("device", device))
                .onErrorResume(e -> {
                    fallbacks.increment();
                    log.warn("Ban lookup unavailable, allowing request: {}", e.getMessage());
                    return Mono.just(false);
                })
                .flatMap(isBanned -> {
                    if (!isBanned) {
                        return chain.filter(exchange);
                    }
                    banned.increment();
                    return ApiErrors.write(exchange.getResponse(), HttpStatus.FORBIDDEN, BANNED_MESSAGE, objectMapper);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
