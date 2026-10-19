package vn.thanhtuanle.gateway;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * The client IP the gateway's own checks (IP bans) use. By default the socket peer: a client's forwarding headers are
 * its own claim, and EdgeHeaders deletes them. Behind the edge ({@value #TRUST_FORWARDED_FOR}, set by the tls profile)
 * the socket peer is Caddy, which writes the browser's address as X-Forwarded-For and discards any the browser sent:
 * there the first hop is the client, but only on a connection that presented the edge's certificate (CN
 * {@value #EDGE_CLIENT}). Any other caller, even one on the allowlist, is its socket peer and cannot name an address.
 */
final class EdgeClientIp {

    static final String TRUST_FORWARDED_FOR = "oj.gateway.edge.trust-forwarded-for";
    static final String EDGE_CLIENT = "edge";
    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private EdgeClientIp() {
    }

    /** True when the flag is on and this connection presented the edge's certificate. */
    static boolean fromEdge(ServerWebExchange exchange, boolean trustForwardedFor) {
        return trustForwardedFor && PeerCertificateWebFilter.peerCommonName(exchange.getRequest().getSslInfo())
                .filter(EDGE_CLIENT::equals).isPresent();
    }

    static String resolve(ServerWebExchange exchange, boolean trustForwardedFor) {
        ServerHttpRequest request = exchange.getRequest();
        if (fromEdge(exchange, trustForwardedFor)) {
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
