package vn.thanhtuanle.gateway;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.SslClosedEngineException;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.PrematureCloseException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which status a gateway failure gets: 503 for what a retry can fix (the upstream is down, restarting or slow), 502 for
 * a TLS fault between the gateway and the service that a retry will not fix.
 */
class JsonErrorWebExceptionHandlerTest {

    @Test
    void aResponseStatusExceptionKeepsItsOwnStatus() {
        assertThat(JsonErrorWebExceptionHandler.statusFor(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anUpstreamThatIsDownOrClosesTheConnectionIs503() {
        assertThat(JsonErrorWebExceptionHandler.statusFor(new ConnectException("Connection refused")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // Also how a TLS 1.3 refusal of the gateway's client certificate surfaces: after the handshake, on the first read.
        assertThat(JsonErrorWebExceptionHandler.statusFor(
                PrematureCloseException.TEST_EXCEPTION)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void aRefusedCertificateIs502() {
        assertThat(JsonErrorWebExceptionHandler.statusFor(
                new DecoderException(new SSLHandshakeException("PKIX path building failed"))))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void aHandshakeTimeoutIs503ThoughItIsAnSslHandshakeException() {
        // SslHandshakeTimeoutException extends SSLHandshakeException: the specific rule must win over the 502 one.
        assertThat(JsonErrorWebExceptionHandler.statusFor(
                new SslHandshakeTimeoutException("handshake timed out after 10000ms")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(JsonErrorWebExceptionHandler.statusFor(
                new DecoderException(new SslHandshakeTimeoutException("handshake timed out after 10000ms"))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void aRetryableCauseAnywhereInTheChainWinsOverAnOuterSslException() {
        assertThat(JsonErrorWebExceptionHandler.statusFor(new SSLException("handshake failed",
                new SslHandshakeTimeoutException("handshake timed out after 10000ms"))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(JsonErrorWebExceptionHandler.statusFor(new DecoderException(new SSLException("closed",
                new ConnectException("Connection reset")))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void aClosedSslEngineIs503() {
        assertThat(JsonErrorWebExceptionHandler.statusFor(new SslClosedEngineException("SSLEngine closed already")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void anythingElseIs500() {
        assertThat(JsonErrorWebExceptionHandler.statusFor(new IllegalStateException("bug")))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
