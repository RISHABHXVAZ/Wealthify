# Production Health and Readiness Probes Deployment Note (OPS-01)

## Overview

This deployment note specifies the operational requirements, endpoints, ports, semantics, and recommended probe configurations for the Wealthify backend container's Spring Boot Actuator liveness and readiness probes.

### Status

- **Application & Container Configuration**: **VERIFIED IN REPOSITORY**
  - Application port, endpoint exposure, health contributor groups, security filter exceptions, sanitized output, and non-root Docker runtime are fully implemented and verified via automated integration tests.
- **Hosting Platform Configuration**: **PENDING**
  - The repository does not currently contain provider-specific deployment manifests (such as Kubernetes manifests, Helm charts, Docker Compose, AWS ECS task definitions, Google Cloud Run service YAML, or Render service manifests).
  - Platform-level probe wiring must be configured on the selected hosting provider according to the specifications in this document.

---

## Verified In-Repository Configuration

### 1. Container & Network Specification

| Parameter | Value | Source / Evidence |
| :--- | :--- | :--- |
| **Runtime Image** | `eclipse-temurin:21-jre-jammy` | [`backend/Dockerfile`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/Dockerfile#L7) |
| **Runtime User** | `wealthify:wealthify` (UID/GID 1001) | [`backend/Dockerfile`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/Dockerfile#L9-L16) |
| **Application Port** | `8080` (TCP, HTTP) | [`application.properties`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/application.properties#L5), [`Dockerfile`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/Dockerfile#L18) |
| **Entrypoint** | `["java", "-jar", "app.jar"]` | [`backend/Dockerfile`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/Dockerfile#L19) |

### 2. Spring Boot Actuator Properties

Configured in [`backend/src/main/resources/application.properties`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/application.properties#L73-L80):

```properties
# Actuator & Production Health / Readiness Endpoints (OPS-01)
management.endpoints.web.exposure.include=health
management.endpoint.health.probes.enabled=true
management.endpoint.health.group.readiness.include=readinessState,db
management.endpoint.health.show-details=never
management.endpoint.health.show-components=never
management.health.mail.enabled=false
```

### 3. Security & Information-Disclosure Protections

Configured in [`backend/src/main/java/com/Wealthify/backend/config/SecurityConfig.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/config/SecurityConfig.java#L52-L56):

- **Public Access**: Unauthenticated `GET` requests to `/actuator/health` and `/actuator/health/**` are permitted without JWT tokens so orchestrators and load balancers can probe health without credentials.
- **Sensitive Endpoints Blocked**: All other Actuator endpoints (`/actuator/env`, `/actuator/beans`, `/actuator/metrics`, `/actuator/configprops`, `/actuator/loggers`, `/actuator/heapdump`, `/actuator/threaddump`) require authentication and return `403 Forbidden` for unauthenticated requests.
- **Sanitized Response Bodies**: `show-details=never` and `show-components=never` ensure probe responses return solely `{"status":"UP"}` or `{"status":"DOWN"}` without leaking database connection URLs, hostnames, usernames, or internal stack traces.
- **Mail Health Contributor Disabled**: `management.health.mail.enabled=false` prevents outbound Gmail SMTP connection attempts from delaying or failing health checks.

---

## Probe Endpoints & Semantics

### 1. Liveness Probe (`/actuator/health/liveness`)

- **Path**: `/actuator/health/liveness`
- **HTTP Method**: `GET`
- **Target Port**: `8080`
- **Contributors Included**: `livenessState`
- **Database Dependency**: **EXCLUDED** (`db: false`)
- **Success Criteria**: HTTP `200 OK` (`{"status":"UP"}`)
- **Failure Criteria**: HTTP `503 Service Unavailable` (`{"status":"DOWN"}`)
- **Operational Purpose**:
  - Determines whether the JVM process is healthy and the Spring ApplicationContext is responsive.
  - **Must not fail during database outages**: Because database checks are excluded, transient database connectivity loss or planned database maintenance will **not** trigger container restarts or crash loops.

### 2. Readiness Probe (`/actuator/health/readiness`)

- **Path**: `/actuator/health/readiness`
- **HTTP Method**: `GET`
- **Target Port**: `8080`
- **Contributors Included**: `readinessState`, `db`
- **Database Dependency**: **INCLUDED** (`db: true`)
- **Success Criteria**: HTTP `200 OK` (`{"status":"UP"}`)
- **Failure Criteria**: HTTP `503 Service Unavailable` (`{"status":"DOWN"}`)
- **Operational Purpose**:
  - Determines whether the application instance is ready to receive incoming HTTP traffic from users.
  - If the database is unreachable or down, readiness immediately reports `503 DOWN`, instructing the orchestrator/load-balancer to pull the container from active traffic routing until database connectivity recovers.

---

## Required Platform Probe Settings (Pending Orchestrator Setup)

When deploying Wealthify to an orchestrator or container platform, configure the probes with the following parameters:

### Recommended Timing & Thresholds

| Parameter | Liveness Probe | Readiness Probe | Rational |
| :--- | :--- | :--- | :--- |
| **Path** | `/actuator/health/liveness` | `/actuator/health/readiness` | Standard Spring Boot probe endpoints |
| **Port** | `8080` | `8080` | Internal container port |
| **Initial Delay Seconds** | `35s` - `45s` | `15s` - `20s` | Allows JVM warmup, classpath scanning, and Hibernate schema validation |
| **Period / Interval Seconds** | `15s` | `10s` | Balances rapid failure detection with low query overhead |
| **Timeout Seconds** | `5s` | `5s` | Avoids premature failure on momentary CPU spikes |
| **Failure Threshold** | `3` (failures before restart) | `2` - `3` (failures before de-routing) | Avoids restarting containers on transient blips |
| **Success Threshold** | `1` | `1` | Fast restoration upon recovery |

---

## Reference Deployment Manifests

The following reference snippets illustrate how to bind the verified probe endpoints across candidate platforms once the hosting target is confirmed.

### 1. Kubernetes Deployment Spec (Reference)

```yaml
spec:
  containers:
    - name: wealthify-backend
      image: <YOUR_REGISTRY>/wealthify-backend:<TAG>
      ports:
        - containerPort: 8080
          name: http
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        runAsGroup: 1001
        readOnlyRootFilesystem: false
        allowPrivilegeEscalation: false
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 40
        periodSeconds: 15
        timeoutSeconds: 5
        failureThreshold: 3
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 20
        periodSeconds: 10
        timeoutSeconds: 5
        failureThreshold: 2
        successThreshold: 1
```

### 2. Docker Compose (Reference for Container Healthcheck)

```yaml
services:
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s
```

### 3. Cloud Run / Render / AWS ECS / Generic PaaS (Reference)

- **Health Check Path**: `/actuator/health/readiness` (for unified routing and health checks on platforms supporting a single health check path)
- **Health Check Port**: `8080`
- **Grace Period / Start Period**: `30` - `45` seconds

---

## Verification Evidence from Repository Tests

The probe configurations and security behaviors are verified by automated integration tests in [`ActuatorSecurityIntegrationTest.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/test/java/com/Wealthify/backend/config/ActuatorSecurityIntegrationTest.java):

1. **Test 1 & 2**: Public `/actuator/health` returns HTTP 200 with `status: UP` and no leaked component metadata.
2. **Test 3a & 3b**: Public `/actuator/health/liveness` and `/actuator/health/readiness` return HTTP 200 with `status: UP`.
3. **Test 4**: Sensitive Actuator endpoints (`/actuator/env`, `/actuator/beans`, `/actuator/metrics`, etc.) return HTTP 403 Forbidden.
4. **Test 5 & 6**: Standard API authorization (`/api/expenses`, `/api/goals`) and public authentication (`/api/auth/**`) remain intact.
5. **Test 7**: Production `application.properties` settings match expected security and group definitions.
6. **Test 8**: Actuator `readiness` group contains `db` and `readinessState`; `liveness` group excludes `db`.
7. **Test 9**: Simulating database unavailability proves:
   - `/actuator/health/readiness` fails with HTTP 503 `status: DOWN`.
   - `/actuator/health/liveness` succeeds with HTTP 200 `status: UP`.
   - Both responses remain strictly sanitized without internal error leakage.
