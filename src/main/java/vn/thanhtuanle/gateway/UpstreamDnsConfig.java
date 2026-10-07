package vn.thanhtuanle.gateway;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Reactor Netty caches a resolved upstream address for as long as the DNS answer says, and Docker's embedded DNS says
 * 600 s. When a service's container stops, its name stops resolving but the gateway kept dialling the old address,
 * which nobody answers, so every request waited out the connect timeout (2 s in sub-project 5's E4). Capped at a
 * second, a stopped service's name fails to resolve within a second and the request gets its 503 at once; a failed
 * lookup is not cached, so a restarted service is found on the next request.
 */
@Configuration(proxyBeanMethods = false)
class UpstreamDnsConfig {

    static final Duration MAX_DNS_CACHE = Duration.ofSeconds(1);

    @Bean
    HttpClientCustomizer upstreamDnsCacheCap() {
        return httpClient -> httpClient.resolver(spec -> spec
                .cacheMaxTimeToLive(MAX_DNS_CACHE)
                .cacheNegativeTimeToLive(Duration.ZERO));
    }
}
