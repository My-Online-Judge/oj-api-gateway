package vn.thanhtuanle.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * @param submissionUri base URI of submission-service inside oj-net
 * @param identityUri   base URI of identity-service inside oj-net
 * @param problemUri    base URI of problem-service inside oj-net
 * @param exposeApiDocs also route Swagger UI / OpenAPI docs to submission-service (dev only)
 */
@ConfigurationProperties("oj.gateway")
public record GatewayRouteProperties(URI submissionUri, URI identityUri, URI problemUri, boolean exposeApiDocs) {
}
