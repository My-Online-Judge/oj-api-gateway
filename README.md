# oj-api-gateway

Edge gateway for My Online Judge (Spring Cloud Gateway 2025.0.3, Spring Boot 3.5, Java 17).
It is the only public entry point for the API: the portal calls `http://<host>:8000/api/v1/**`
and the gateway forwards to the services behind it. See the design in
`my-oj/docs/superpowers/specs/2026-09-27-microservices-split-design.md`.

## What it does

| Concern | Where |
|---|---|
| Routing table: identity, problem and submission paths to their services; any other path is a 404 from the gateway | `GatewayRoutes` |
| Client-IP trust boundary: client `X-Forwarded-*`/`X-Real-IP`/`Forwarded` are deleted, then `X-Forwarded-For` = socket peer | `EdgeHeaders`, `trusted-proxies` in `application.yml` |
| Browser `Host` is forwarded unchanged | `EdgeHeaders` |
| CORS for the portal origins; upstream `Access-Control-*` headers are dropped | `application.yml` (`globalcors`), `EdgeHeaders` |
| Gateway errors in judge-api's `ApiResponse` shape (503 when an upstream is down) | `JsonErrorWebExceptionHandler` |
| Actuator on port 8081 only (never published) | `application.yml` (`management.server.port`) |
| IP/device ban check (Redis mirror written by judge-api); 403 + `oj.request.banned`; fails open | `AccessBanFilter`, `RedisLookups` |
| Revoked access token (logout blocklist, per-user `revoked-before` cutoff) is stripped, not rejected; fails open | `RevokedTokenFilter`, `RedisLookups` |

Edge rules are global filters on purpose: Spring Cloud Gateway applies `default-filters` only to
property-defined routes, not to the Java DSL routes in `GatewayRoutes`.

## Ports and configuration

| Port | Purpose |
|---|---|
| 8000 | public API |
| 8081 | actuator: `/actuator/health`, `/actuator/prometheus` |

| Env | Default | Meaning |
|---|---|---|
| `SUBMISSION_URI` | `http://submission-service:8000` | upstream for `/api/v1/{submissions,languages,judge-servers}/**` |
| `IDENTITY_URI` | `http://identity-service:8000` | upstream for `/api/v1/{auth,users,roles,permissions,security}/**` |
| `PROBLEM_URI` | `http://problem-service:8000` | upstream for `/api/v1/problems` and `/api/v1/problems/**` |
| `SPRING_PROFILES_ACTIVE` | — | `dev` also routes Swagger UI / `/v3/api-docs` to submission-service |
| `JAVA_TOOL_OPTIONS` | — | `-javaagent:/otel/opentelemetry-javaagent.jar` enables tracing (agent is in the image) |
| `REDIS_HOST` | `localhost` | Redis holding the ban mirror, logout blocklist and revocation cutoffs |
| `REDIS_PORT` | `6379` | |

## Test

    ./mvnw verify

`oj-common` must be installed first (`./mvnw install` in the sibling `oj-common` repo); Docker builds
compile it from the named build context: `docker build --build-context oj-common=../oj-common .`
