# ClickKart Eureka Discovery Server

Service discovery registry for the ClickKart microservices platform. This is **item #1** in
the fixed ClickKart build order (Eureka → Config Server → Gateway → Auth → ... → Admin) and
is intentionally self-contained: it is the foundation every other service discovers through,
so it cannot itself depend on the Config Server (item #2), which doesn't exist yet at this
point in the build.

## Tech stack

| Layer | Version |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.7 |
| Cloud | Spring Cloud 2025.1.2 ("Oakwood") / Spring Cloud Netflix 5.0.2 |
| Security | Spring Security (HTTP Basic) |
| Build | Maven |
| Container | Docker / Docker Compose |

## Project structure

```
clickkart-eureka-server/
├── pom.xml
├── Dockerfile
├── docker/
│   ├── docker-compose.yml          # base service definition
│   ├── docker-compose.dev.yml      # dev overrides
│   ├── docker-compose.test.yml     # test overrides
│   ├── docker-compose.qa.yml       # qa overrides
│   └── docker-compose.prod.yml     # prod overrides
└── src/
    ├── main/java/com/clickkart/eureka/
    │   ├── EurekaServerApplication.java
    │   └── config/
    │       ├── SecurityConfig.java
    │       └── RequiredProdSecretsConfig.java
    ├── main/resources/
    │   ├── application.properties          # shared defaults
    │   ├── application-dev.properties
    │   ├── application-test.properties
    │   ├── application-qa.properties
    │   └── application-prod.properties
    └── test/java/com/clickkart/eureka/
        └── EurekaServerApplicationTests.java
```

## Configuration & profiles

Config is **not** sourced from the Config Server — see "Design decisions" below. All four
profiles live locally as `.properties` files and are selected via `SPRING_PROFILES_ACTIVE`.

| Profile | Purpose | Self-preservation | Dashboard credentials | Peer URLs |
|---|---|---|---|---|
| `dev` | Local Eclipse development | off | `EUREKA_DASHBOARD_USERNAME` / `PASSWORD`, defaults to `admin` / `dev-only-secret-change-me` | single node, self |
| `test` | CI / automated runs | off | **required, no default** | single node, self |
| `qa` | Shared QA environment | on | **required, no default** | optional `EUREKA_PEER_URLS`, falls back to self if unset |
| `prod` | Production | on | **required, no default** | **required, no default** (`EUREKA_PEER_URLS`) |

### Environment variables

| Variable | Used in | Required? | Notes |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | all | no (defaults to `dev`) | selects the profile |
| `SERVER_PORT` | all | no (defaults to `8761`) | |
| `EUREKA_HOSTNAME` | all | required in `prod` | hostname this instance advertises to peers |
| `EUREKA_DASHBOARD_USERNAME` / `EUREKA_DASHBOARD_PASSWORD` | all | required in `test`/`qa`/`prod` | HTTP Basic credentials for the dashboard and `/eureka/**` API |
| `EUREKA_PEER_URLS` | `qa`, `prod` | required in `prod`, optional in `qa` | comma-separated peer replica URLs for HA clustering |

`test`/`qa`/`prod` have **no fallback defaults** for secrets — the app fails fast at startup
if they're unset (verified; see below).

## Security

`/eureka/**` (registration API) and the dashboard are protected with HTTP Basic auth so the
registry can't be scraped or polluted by unauthenticated clients. Only `/actuator/health` is
open, for container/Kubernetes liveness and readiness probes. CSRF is disabled because Eureka
clients register/renew/deregister via stateless REST calls with no CSRF token support — this
matches Spring Cloud Netflix's own documented approach.

Other services authenticate against this registry via:

```properties
eureka.client.service-url.defaultZone=http://<user>:<pass>@eureka-server:8761/eureka/
```

## Running locally (Eclipse)

1. **Right-click `EurekaServerApplication.java` → Run As → Spring Boot App** (defaults to `dev`).
2. For another profile: **Run → Run Configurations → Spring Boot App** → new config → VM
   arguments: `-Dspring.profiles.active=test` (add `-DEUREKA_DASHBOARD_USERNAME=... -DEUREKA_DASHBOARD_PASSWORD=...`
   for `test`/`qa`/`prod`, since those have no default).
3. Confirm startup: console shows `Started EurekaServerApplication`; browse
   `http://localhost:8761` (Basic Auth prompt); `http://localhost:8761/actuator/health`
   returns `{"status":"UP"}` with no auth required.

Maven CLI alternative:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Dspring.profiles.active=dev
```

## Running in Docker

```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up --build -d
```

Swap `docker-compose.dev.yml` for `.test.yml` / `.qa.yml` / `.prod.yml` to run other
profiles; `qa`/`prod` overrides require `EUREKA_DASHBOARD_USERNAME`, `EUREKA_DASHBOARD_PASSWORD`,
and (for `prod`) `EUREKA_HOSTNAME` / `EUREKA_PEER_URLS` to be set in the host shell or a
`.env` file — sourced from a real secrets manager in actual deployments, never committed.

## Design decisions

- **Standalone config, not Config Server-backed.** Eureka is infrastructure item #1; Config
  Server is item #2. Having Eureka depend on Config Server (or vice versa for its own
  bootstrap) creates a circular startup dependency. This matches common production Spring
  Cloud practice: Eureka is self-configured, and Config Server can optionally register with
  it *as a client* later, once both exist.
- **`RequiredProdSecretsConfig` exists because of a real bug found during verification.**
  `eureka.client.service-url.defaultZone=${EUREKA_PEER_URLS}` binds into a
  `Map<String,String>` on `EurekaClientConfigBean`. Spring's relaxed binder does not apply
  the same strict placeholder-resolution check to map values that it applies to scalar
  properties (e.g. `spring.security.user.password`). Without this bean, `prod` would boot
  "successfully" with a literal, unresolved `${EUREKA_PEER_URLS}` string, report
  `/actuator/health` as `UP`, and silently fail to cluster. The bean forces the same
  fail-fast behavior `@Value` already gives scalar properties.

## Verification performed

All four profiles were built and run as real containers (not just config review):

| Profile | Boots with required env | Fails fast without required env | Health (no auth) | `/eureka/apps` unauthenticated | `/eureka/apps` authenticated |
|---|---|---|---|---|---|
| dev | ✅ | n/a | 200 | 401 | 200 |
| test | ✅ | ✅ | 200 | — | — |
| qa | ✅ (peer self-fallback) | n/a | 200 | — | — |
| prod | ✅ | ✅ | 200 | — | — |

## Deployment-Ready Checklist

- [x] Builds and runs standalone (Eclipse + Maven)
- [x] N/A: does not consume Config Server (by design, see above)
- [x] Resolves correctly under all 4 profiles — verified by booting each in a container
- [x] No DB/migration (in-memory registry); zero stub/TODO methods
- [x] No Feign clients (nothing to depend on yet)
- [x] Docker image builds and runs; compose base + 4 per-env overrides in place
- [x] Secrets: `dev` has a flagged fake default; `test`/`qa`/`prod` fail fast when unset — confirmed by omitting them and watching the container die
- [x] Correlation-ID rule N/A — Eureka has no business request path
- [x] Every file labeled with its exact path
- [x] Dashboard + `/eureka/**` secured with HTTP Basic; only `/actuator/health` open
- [x] No tutorial shortcuts — found and fixed a silent-failure gap in prod's fail-fast guarantee

**Status: Eureka confirmed deployable.** Next up per the locked build order: Config Server.
