package vn.thanhtuanle.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Strangler Fig routing table. Each sub-project moves one group of paths from the monolith to
 * its new service here; whatever is not claimed falls through to the monolith catch-all.
 */
@Configuration(proxyBeanMethods = false)
class GatewayRoutes {

    /** Owned by identity-service since sub-project 1b. */
    static final String[] IDENTITY_PATHS = {
            "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**", "/api/v1/security/**"};

    /** Owned by problem-service since sub-project 2b. */
    static final String[] PROBLEM_PATHS = {"/api/v1/problems", "/api/v1/problems/**"};

    @Bean
    RouteLocator ojRoutes(RouteLocatorBuilder builder, GatewayRouteProperties props) {
        RouteLocatorBuilder.Builder routes = builder.routes()
                // Explicit orders: the specific service routes must win over the catch-all below.
                .route("identity-api", r -> r.order(0).path(IDENTITY_PATHS).uri(props.identityUri()))
                .route("problem-api", r -> r.order(0).path(PROBLEM_PATHS).uri(props.problemUri()))
                .route("monolith-api", r -> r.order(1).path("/api/v1/**").uri(props.monolithUri()));
        if (props.exposeApiDocs()) {
            routes.route("monolith-api-docs", r -> r
                    .path("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .uri(props.monolithUri()));
        }
        return routes.build();
    }
}
