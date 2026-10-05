package vn.thanhtuanle.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The routing table: every public API path belongs to exactly one service. Since sub-project 3a nothing
 * falls through to a catch-all — an unclaimed path is the gateway's own 404.
 */
@Configuration(proxyBeanMethods = false)
class GatewayRoutes {

    /** Owned by identity-service since sub-project 1b. */
    static final String[] IDENTITY_PATHS = {
            "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**", "/api/v1/security/**"};

    /** Owned by submission-service (oj-submission-service). */
    static final String[] SUBMISSION_PATHS = {"/api/v1/submissions/**", "/api/v1/languages/**", "/api/v1/judge-servers/**"};

    /** Owned by problem-service since sub-project 2b. */
    static final String[] PROBLEM_PATHS = {"/api/v1/problems", "/api/v1/problems/**"};

    @Bean
    RouteLocator ojRoutes(RouteLocatorBuilder builder, GatewayRouteProperties props) {
        RouteLocatorBuilder.Builder routes = builder.routes()
                .route("identity-api", r -> r.order(0).path(IDENTITY_PATHS).uri(props.identityUri()))
                .route("problem-api", r -> r.order(0).path(PROBLEM_PATHS).uri(props.problemUri()))
                .route("submission-api", r -> r.order(0).path(SUBMISSION_PATHS).uri(props.submissionUri()));
        if (props.exposeApiDocs()) {
            routes.route("submission-api-docs", r -> r
                    .path("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .uri(props.submissionUri()));
        }
        return routes.build();
    }
}
