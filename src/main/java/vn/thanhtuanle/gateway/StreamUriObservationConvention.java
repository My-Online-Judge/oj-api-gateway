package vn.thanhtuanle.gateway;

import io.micrometer.common.KeyValue;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * A proxied request has no handler pattern, so Boot tags all of them {@code uri="UNKNOWN"}. SSE verdict streams last
 * as long as judging; tagged apart, the latency alerts can leave them out. Replaces Boot's default convention, which
 * is {@code @ConditionalOnMissingBean}.
 */
@Component
class StreamUriObservationConvention extends DefaultServerRequestObservationConvention {

    private static final Pattern STREAM = Pattern.compile("^/api/v1/submissions/[^/]+/stream$");
    private static final KeyValue STREAM_URI = KeyValue.of("uri", "/api/v1/submissions/{id}/stream");

    @Override
    protected KeyValue uri(ServerRequestObservationContext context) {
        if (STREAM.matcher(context.getCarrier().getPath().value()).matches()) {
            return STREAM_URI;
        }
        return super.uri(context);
    }
}
