# oj-api-gateway

Edge gateway for My Online Judge (Spring Cloud Gateway 2025.0.3, Spring Boot 3.5, Java 17).
It is the entry point for the API: the portal calls `https://<host>/api/v1/**` on the HTTPS edge (Caddy,
judge-deployment), which proxies to the gateway over mutual TLS, and the gateway forwards to the services behind it. See the design in
`my-oj/docs/superpowers/specs/2026-09-27-microservices-split-design.md`.

## What it does

| Concern | Where |
|---|---|
| Routing table: identity, problem and submission paths to their services; any other path is a 404 from the gateway | `GatewayRoutes` |
| Client-IP trust boundary: client `X-Forwarded-*`/`X-Real-IP`/`Forwarded` are deleted, then `X-Forwarded-For` = socket peer. Behind the edge (`oj.gateway.edge.trust-forwarded-for`, `tls` profile) Caddy's `X-Forwarded-For` is kept and its first hop is the client IP | `EdgeHeaders`, `EdgeClientIp`, `trusted-proxies` in `application.yml` |
| Browser `Host` is forwarded unchanged | `EdgeHeaders` |
| CORS for the portal origins; upstream `Access-Control-*` headers are dropped | `application.yml` (`globalcors`), `EdgeHeaders` |
| Gateway errors in the services' `ApiResponse` shape (oj-common) (503 when an upstream is down, restarting or too slow to finish the TLS handshake; 502 when the gateway refuses its certificate) | `JsonErrorWebExceptionHandler` |
| Actuator on port 8081 only (never published) | `application.yml` (`management.server.port`) |
| `tls` profile: upstream calls over mutual TLS (the `oj` bundle's certificate presented, the service's verified against the CA and the route URI's host); actuator TLS with a client certificate required | `application-tls.yml` |
| `tls` profile: port 8000 serves TLS, requires a client certificate and answers only the CNs in `oj.tls.allowed-clients` (`edge`); others get 403 | `PeerCertificateWebFilter`, `application-tls.yml` |
| IP/device ban check (Redis mirror written by identity-service); 403 + `oj.request.banned`; fails open | `AccessBanFilter`, `RedisLookups` |
| Revoked access token (logout blocklist, per-user `revoked-before` cutoff) is stripped, not rejected; fails open | `RevokedTokenFilter`, `RedisLookups` |

Edge rules are global filters on purpose: Spring Cloud Gateway applies `default-filters` only to
property-defined routes, not to the Java DSL routes in `GatewayRoutes`.

## Ports and configuration

| Port | Purpose |
|---|---|
| 8000 | API (`tls` profile: HTTPS, client certificate CN `edge` only) |
| 8081 | actuator: `/actuator/health`, `/actuator/prometheus` (`tls` profile: HTTPS, client certificate required) |

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
