package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renders every error the gateway itself produces in judge-api's {@code ApiResponse} error shape
 * ({@code {status, message, timestamp}}), so the portal handles a gateway failure exactly like a
 * failure inside a service.
 */
@Component
@Order(-2) // ahead of Spring Boot's DefaultErrorWebExceptionHandler (order -1)
class JsonErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    static final String UNAVAILABLE_MESSAGE = "Service temporarily unavailable";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ObjectMapper objectMapper;

    JsonErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    record ErrorBody(int status, String message, String timestamp) {
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex); // failed mid-stream (e.g. an SSE body): nothing sane left to write
        }
        HttpStatusCode status = statusFor(ex);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(
                    new ErrorBody(status.value(), messageFor(status), LocalDateTime.now().format(TIMESTAMP)));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    static HttpStatusCode statusFor(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getStatusCode();
        }
        for (Throwable t = ex; t != null; t = t.getCause()) {
            // Connection refused and connect timeout (ConnectTimeoutException extends ConnectException),
            // DNS failure ("Failed to resolve 'judge-api'" when the container is gone), and the upstream
            // closing the connection before it answered (restarting, or a stale pooled keep-alive).
            if (t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof PrematureCloseException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String messageFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            return UNAVAILABLE_MESSAGE;
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : "Error";
    }
}
