package vn.thanhtuanle.gateway;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * The client IP the gateway's own checks (IP bans) use. By default the socket peer: a client's forwarding headers are
 * its own claim, and EdgeHeaders deletes them. Behind the edge ({@value #TRUST_FORWARDED_FOR}, set by the tls profile)
 * the socket peer is Caddy, which writes the browser's address as X-Forwarded-For and discards any the browser sent,
 * and port 8000 answers only the edge's certificate (PeerCertificateWebFilter): there the first hop is the client.
 */
final class EdgeClientIp {

    static final String TRUST_FORWARDED_FOR = "oj.gateway.edge.trust-forwarded-for";
    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private EdgeClientIp() {
    }

    static String resolve(ServerWebExchange exchange, boolean trustForwardedFor) {
        ServerHttpRequest request = exchange.getRequest();
        if (trustForwardedFor) {
            String forwarded = request.getHeaders().getFirst(X_FORWARDED_FOR);
            if (forwarded != null) {
                String firstHop = forwarded.split(",", 2)[0].trim();
                if (!firstHop.isEmpty()) {
                    return firstHop;
                }
            }
        }
        InetSocketAddress peer = request.getRemoteAddress();
        return peer == null ? null : peer.getHostString();
    }
}
