# oj-api-gateway

Edge gateway for My Online Judge (Spring Cloud Gateway 2025.0.3, Spring Boot 3.5, Java 17).
It is the only public entry point for the API: the portal calls `http://<host>:8000/api/v1/**`
and the gateway forwards to the services behind it. See the design in
`my-oj/docs/superpowers/specs/2026-09-27-microservices-split-design.md`.

## What it does

| Concern | Where |
|---|---|
| Routing table (Strangler Fig: today everything → judge-api) | `GatewayRoutes` |
| Client-IP trust boundary: client `X-Forwarded-*`/`X-Real-IP`/`Forwarded` are deleted, then `X-Forwarded-For` = socket peer | `EdgeHeaders`, `trusted-proxies` in `application.yml` |
| Browser `Host` is forwarded unchanged | `EdgeHeaders` |
| CORS for the portal origins; upstream `Access-Control-*` headers are dropped | `application.yml` (`globalcors`), `EdgeHeaders` |
| Gateway errors in judge-api's `ApiResponse` shape (503 when an upstream is down) | `JsonErrorWebExceptionHandler` |
| Actuator on port 8081 only (never published) | `application.yml` (`management.server.port`) |

Edge rules are global filters on purpose: Spring Cloud Gateway applies `default-filters` only to
property-defined routes, not to the Java DSL routes in `GatewayRoutes`.

## Ports and configuration

| Port | Purpose |
|---|---|
| 8000 | public API |
| 8081 | actuator: `/actuator/health`, `/actuator/prometheus` |

| Env | Default | Meaning |
|---|---|---|
| `MONOLITH_URI` | `http://judge-api:8000` | upstream for `/api/v1/**` |
| `SPRING_PROFILES_ACTIVE` | — | `dev` also routes Swagger UI / `/v3/api-docs` to judge-api |
| `JAVA_TOOL_OPTIONS` | — | `-javaagent:/otel/opentelemetry-javaagent.jar` enables tracing (agent is in the image) |

## Test

    ./mvnw verify
