package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.SslInfo;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import vn.thanhtuanle.oj.common.tls.AllowedClients;
import vn.thanhtuanle.oj.common.tls.OjTlsProperties;
import vn.thanhtuanle.oj.common.tls.PeerCertificates;

import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;

/**
 * Answers 403 to every request whose client certificate CN is missing or not in {@code oj.tls.allowed-clients}: under the
 * tls profile port 8000 requires a client certificate (server.ssl.client-auth: need) and only the edge's gets past this
 * filter, before routing and before any gateway filter. oj-common's PeerCertificateFilter does the same for the servlet
 * services, and the same 403 body, but it does not run on WebFlux.
 * <p>
 * WebFlux also hands this context's WebFilters to the actuator's server (management.server.port), whose callers are
 * Prometheus and the healthcheck with their own certificates (the port requires one): requests that server handles are
 * let through.
 */
class PeerCertificateWebFilter implements WebFilter, Ordered {

    static final String NOT_ALLOWED_MESSAGE = "Client certificate not allowed";

    /** The server namespace Spring Boot gives the actuator's own server and context. */
    private static final String MANAGEMENT_NAMESPACE = "management";

    private static final Logger log = LoggerFactory.getLogger(PeerCertificateWebFilter.class);

    private final Set<String> allowed;
    private final ObjectMapper objectMapper;

    PeerCertificateWebFilter(Set<String> allowed, ObjectMapper objectMapper) {
        this.allowed = Set.copyOf(allowed);
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (WebServerApplicationContext.hasServerNamespace(exchange.getApplicationContext(), MANAGEMENT_NAMESPACE)) {
            return chain.filter(exchange);
        }
        Optional<String> cn = peerCommonName(exchange.getRequest().getSslInfo());
        if (cn.isPresent() && allowed.contains(cn.get())) {
            return chain.filter(exchange);
        }
        log.warn("Rejected client certificate, CN={}", cn.orElse("<none>"));
        return ApiErrors.write(exchange.getResponse(), HttpStatus.FORBIDDEN, NOT_ALLOWED_MESSAGE, objectMapper);
    }

    /** The CN of the client certificate this connection presented; empty without TLS or a certificate. */
    static Optional<String> peerCommonName(SslInfo ssl) {
        X509Certificate[] peers = ssl == null ? null : ssl.getPeerCertificates();
        return peers == null || peers.length == 0 ? Optional.empty() : PeerCertificates.commonName(peers[0]);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OjTlsProperties.class)
    static class Registration {

        /** Under client auth (the tls profile) an empty or blank-only allowlist fails the startup (oj-common rule). */
        @Bean
        @Conditional(AllowedClients.Enforced.class)
        PeerCertificateWebFilter peerCertificateWebFilter(OjTlsProperties properties, ObjectMapper objectMapper) {
            return new PeerCertificateWebFilter(AllowedClients.required(properties), objectMapper);
        }
    }
}
