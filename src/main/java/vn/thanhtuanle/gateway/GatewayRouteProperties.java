package vn.thanhtuanle.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * @param monolithUri   base URI of judge-api inside oj-net
 * @param identityUri   base URI of identity-service inside oj-net
 * @param problemUri    base URI of problem-service inside oj-net
 * @param exposeApiDocs also route Swagger UI / OpenAPI docs to the monolith (dev only)
 */
@ConfigurationProperties("oj.gateway")
public record GatewayRouteProperties(URI monolithUri, URI identityUri, URI problemUri, boolean exposeApiDocs) {
}
