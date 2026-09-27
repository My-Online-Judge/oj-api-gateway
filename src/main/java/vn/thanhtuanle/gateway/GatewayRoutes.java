package vn.thanhtuanle.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The Strangler Fig routing table. In sub-project 0 every public API path still goes to the
 * monolith; each later sub-project moves one group of paths to its new service here.
 */
@Configuration(proxyBeanMethods = false)
class GatewayRoutes {

    @Bean
    RouteLocator ojRoutes(RouteLocatorBuilder builder, GatewayRouteProperties props) {
        RouteLocatorBuilder.Builder routes = builder.routes()
                .route("monolith-api", r -> r.path("/api/v1/**").uri(props.monolithUri()));
        if (props.exposeApiDocs()) {
            routes.route("monolith-api-docs", r -> r
                    .path("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .uri(props.monolithUri()));
        }
        return routes.build();
    }
}
