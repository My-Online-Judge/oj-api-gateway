package vn.thanhtuanle.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;

/**
 * Edge rules for EVERY route, however it is declared. They are deliberately not
 * {@code spring.cloud.gateway.server.webflux.default-filters}: Spring Cloud Gateway applies default
 * filters only to property-defined routes, never to Java DSL routes like {@link GatewayRoutes}, so a
 * route added there would silently lose the client-IP protection.
 */
@Configuration(proxyBeanMethods = false)
class EdgeHeaders {

    /** Forwarding headers a client could send to lie about its IP, host or scheme. */
    static final List<String> CLIENT_FORWARDING_HEADERS = List.of(
            "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Forwarded-Port",
            "X-Forwarded-Prefix", "X-Real-IP", "Forwarded");

    /**
     * Strips client-supplied forwarding headers before routing, so the X-Forwarded-For that Spring
     * Cloud Gateway then writes (see trusted-proxies in application.yml) holds exactly the socket
     * peer; and forwards the Host the browser sent, as judge-api saw it before the gateway existed.
     */
    @Bean
    GlobalFilter clientForwardingHeadersFilter() {
        return new ClientForwardingHeadersFilter();
    }

    private static final class ClientForwardingHeadersFilter implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(headers -> CLIENT_FORWARDING_HEADERS.forEach(headers::remove))
                    .build();
            ServerWebExchange edged = exchange.mutate().request(request).build();
            edged.getAttributes().put(PRESERVE_HOST_HEADER_ATTRIBUTE, true);
            return chain.filter(edged);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
