package vn.thanhtuanle.gateway;

import reactor.core.publisher.Mono;

/** Whether an IP or device is currently banned. */
public interface BanLookup {

    /**
     * @param type  {@code "ip"} or {@code "device"}
     * @param value the IP (socket peer) or the {@code ClientFingerprint} device hash; null/blank is never banned
     */
    Mono<Boolean> isBanned(String type, String value);
}
