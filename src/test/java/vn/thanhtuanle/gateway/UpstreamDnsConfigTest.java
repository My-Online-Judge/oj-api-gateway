package vn.thanhtuanle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.NameResolverProvider;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker's DNS answers with a 600 s TTL. Cached that long, a stopped service's old address keeps taking the
 * gateway's connections until each one times out (sub-project 5, E4 C1/C5: 2 s per request).
 */
class UpstreamDnsConfigTest extends GatewayTestSupport {

    @Autowired
    HttpClient httpClient;

    @Test
    void theGatewayForgetsAnUpstreamAddressWithinASecondAndNeverCachesAFailedLookup() {
        NameResolverProvider resolver = httpClient.configuration().getNameResolverProvider();

        assertThat(resolver).as("the DNS resolver of the gateway's HttpClient").isNotNull();
        assertThat(resolver.cacheMaxTimeToLive()).isLessThanOrEqualTo(Duration.ofSeconds(1));
        assertThat(resolver.cacheNegativeTimeToLive()).isZero();
    }
}
