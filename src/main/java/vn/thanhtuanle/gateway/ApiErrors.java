package vn.thanhtuanle.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Writes judge-api's {@code ApiResponse} error shape: {@code {status, message, timestamp}}. */
final class ApiErrors {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    record ErrorBody(int status, String message, String timestamp) {
    }

    private ApiErrors() {
    }

    static Mono<Void> write(ServerHttpResponse response, HttpStatusCode status, String message,
                            ObjectMapper objectMapper) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(
                    new ErrorBody(status.value(), message, LocalDateTime.now().format(TIMESTAMP)));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
