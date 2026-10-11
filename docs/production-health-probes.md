# Production Health and Readiness Probes Deployment Runbook (OPS-01)

## Overview

This runbook specifies the production deployment architecture, health probe semantics, port binding, and configuration procedures for the Wealthify backend on **Render**, paired with the frontend on **Vercel** and the PostgreSQL database on **Neon Cloud**.

### Target Deployment Topology

| Component | Hosting Platform | Deployment Artifact | Primary Network / Port |
| :--- | :--- | :--- | :--- |
| **Frontend** | **Vercel** | SPA build ([`vercel.json`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/frontend/src/vercel.json)) | Client-side routing to Render backend |
| **Backend** | **Render** | Docker container ([`backend/Dockerfile`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/Dockerfile)) | HTTP port `${PORT:8080}` |
| **Database** | **Neon Cloud** | Serverless PostgreSQL | TLS connection via `SPRING_DATASOURCE_URL` |

### Configuration Status

- **Application & Container**: **VERIFIED IN REPOSITORY**
  - Actuator probe groups, port binding (`${PORT:8080}`), security filter permit, output sanitization, and non-root execution (`wealthify:wealthify`) are committed and verified by automated integration tests.
- **Render Dashboard Settings**: **PENDING OPERATOR CONFIGURATION**
  - The repository does not contain a declarative `render.yaml` Blueprint. The Health Check Path must be verified and configured directly in the Render Dashboard by the service operator.

---

## Port Configuration & Dynamic Port Binding

Render automatically provisions and assigns an internal listening port via the `PORT` environment variable (commonly defaulting to `10000` or a dynamically assigned port).

In [`backend/src/main/resources/application.properties`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/application.properties#L4-L5):
```properties
# Server Config
server.port=${PORT:8080}
```

### Operational Behavior
- **On Render**: When Render injects the `PORT` environment variable at container startup, the Spring Boot embedded Tomcat server automatically binds to `${PORT}`, allowing Render's routing mesh to forward traffic to the container.
- **Local Development & Standalone Docker**: When the `PORT` environment variable is unset, the application safely defaults to port `8080`, preserving compatibility with local development, `./mvnw spring-boot:run`, and local `docker run` workflows.

---

## Render Health Check Path Configuration

### Instructions for the Render Dashboard

To configure health checks for the Wealthify backend web service:

1. Log in to the [Render Dashboard](https://dashboard.render.com).
2. Select your backend Web Service (e.g., `wealthify-backend`).
3. In the left-hand navigation menu, click **Settings**.
4. Scroll down to the **Health Check Path** field.
5. Enter:
   ```text
   /actuator/health/readiness
   ```
6. Click **Save Changes**.

> [!IMPORTANT]
> This action must be executed in the Render Dashboard. The repository does not manage cloud dashboard settings directly; this runbook documents the required specification.

---

## Single-Path Model vs. Dual Probes: Architecture & Trade-Offs

### 1. Render's Single Health Check Model

The Wealthify backend exposes two distinct Actuator probe endpoints:
- `/actuator/health/liveness` (JVM & ApplicationContext liveness; **excludes** database)
- `/actuator/health/readiness` (Traffic readiness; **includes** database connectivity)

**Render evaluates a single HTTP health check path.** Render does **not** independently poll both liveness and readiness probes concurrently (unlike Kubernetes).

- **Deployment Phase (Zero-Downtime Rollouts)**:
  During a deployment, Render spins up the new container and polls the configured Health Check Path. Render will only switch live user traffic to the new instance once the endpoint returns HTTP `200 OK`. If the check fails or times out, the deployment fails and old traffic remains on the previous instance.
- **Runtime Phase (Continuous Health Monitoring)**:
  Once live, Render continuously issues periodic HTTP `GET` requests to the configured path. If consecutive requests fail, Render marks the instance unhealthy, stops routing user requests to it, and will restart the container instance.

---

### 2. Evaluation: Why `/actuator/health/readiness` is Recommended

We recommend setting Render's Health Check Path to `/actuator/health/readiness` rather than `/actuator/health/liveness` or the root `/actuator/health`:

| Health Endpoint | Components Checked | Pros | Cons on Render |
| :--- | :--- | :--- | :--- |
| **`/actuator/health/readiness`** *(Recommended)* | `readinessState`, `db` | Verifies both Spring Boot application initialization AND database connectivity. Prevents routing users to an instance that cannot execute transactions or auth. | If the database experiences a prolonged outage, Render will mark the service unhealthy and restart the container. |
| **`/actuator/health/liveness`** | `livenessState` (JVM only) | Will not restart the container during database outages. | Will report `200 OK` even if database connectivity is completely down, causing Render to route user requests into failing 500 errors. |
| **`/actuator/health`** | All active contributors | Checks overall health. | Includes diskSpace and other contributors that are unnecessary for routing decisions. |

---

### 3. The Operational Trade-Off: Prolonged Database Outages

Operators must understand the trade-off of including database health in Render's single health check path:

1. **Detection of Outages**:
   If the Neon PostgreSQL database is unreachable (e.g., network partition, SSL handshake failure, connection pool exhaustion, or database maintenance), `/actuator/health/readiness` immediately returns HTTP `503 Service Unavailable` (`{"status":"DOWN"}`).
2. **Render Restart Consequence**:
   Because Render relies on this single path, prolonged 503 responses will cause Render to de-route traffic and restart the web service container.
3. **Recovery Dynamics**:
   When the container restarts, it will boot up and re-probe database connectivity. If the database is still offline, the container will remain unhealthy until database connectivity is restored.
4. **Decoupled Internal Liveness**:
   Within the application code, liveness (`/actuator/health/liveness`) deliberately excludes `db` (`db: false`). If the application is ever migrated to an orchestrator that supports independent liveness and readiness probes (such as Kubernetes or AWS ECS), liveness will remain `200 UP` during database outages, completely avoiding container restart loops while readiness correctly halts traffic routing.

---

## Information-Disclosure & Security Verification

All health endpoints are hardened against information leakage in [`SecurityConfig.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/java/com/Wealthify/backend/config/SecurityConfig.java) and [`application.properties`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/main/resources/application.properties):

1. **Sanitized Output**:
   ```properties
   management.endpoint.health.show-details=never
   management.endpoint.health.show-components=never
   ```
   Both HTTP 200 (`UP`) and HTTP 503 (`DOWN`) responses return minimal JSON (`{"status":"UP"}` or `{"status":"DOWN"}`) without exposing database URLs, hostnames, usernames, disk space metrics, or internal stack traces.
2. **Access Control**:
   - `/actuator/health` and `/actuator/health/**` are accessible without JWT tokens to permit Render's proxy to probe health.
   - All sensitive Actuator endpoints (`/actuator/env`, `/actuator/beans`, `/actuator/metrics`, `/actuator/configprops`, `/actuator/heapdump`) require authentication and reject unauthenticated requests with HTTP `403 Forbidden`.
3. **Mail Contributor Disabled**:
   `management.health.mail.enabled=false` prevents outbound Gmail SMTP connections from blocking or failing the health probe.

---

## Verification Evidence from Repository Tests

All probe semantics, security configurations, and dynamic port behaviors are verified by automated integration tests in [`ActuatorSecurityIntegrationTest.java`](file:///c:/Users/Rishabh%20Srivastava/OneDrive/Desktop/Wealthify/backend/src/test/java/com/Wealthify/backend/config/ActuatorSecurityIntegrationTest.java):

- **Port Configuration**: Asserts `server.port` equals `${PORT:8080}` and resolves to `8080` when `PORT` is unset.
- **Probe Accessibility**: Asserts `/actuator/health/readiness` and `/actuator/health/liveness` are reachable without authentication.
- **Output Sanitization**: Asserts details and component trees are suppressed.
- **Sensitive Actuator Protection**: Asserts `/actuator/env`, `/actuator/beans`, etc. return HTTP 403 Forbidden.
- **Database Outage Simulation**: Replaces the `db` contributor with a simulated failure and verifies:
  - `/actuator/health/readiness` returns HTTP 503 `status: DOWN`.
  - `/actuator/health/liveness` remains HTTP 200 `status: UP`.
  - No sensitive database exception details are leaked in the response.
