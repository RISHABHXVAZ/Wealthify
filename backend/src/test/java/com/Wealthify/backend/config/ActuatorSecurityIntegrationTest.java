package com.Wealthify.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OPS-01 Actuator & Production Health Security Integration Test Suite:
 * Verifies:
 * 1. Public health endpoint /actuator/health is accessible without authentication.
 * 2. Unauthenticated health response is strictly sanitized ({"status":"UP"} only, no details/components).
 * 3. Liveness and readiness probe endpoints (/actuator/health/liveness, /actuator/health/readiness) work properly.
 * 4. Unrelated/sensitive Actuator endpoints (/actuator/env, /actuator/beans, /actuator/metrics, etc.) are protected.
 * 5. Protecting Actuator does not affect application API security (/api/expenses, /api/goals require auth).
 * 6. Public authentication endpoints (/api/auth/**) remain accessible.
 */
@SpringBootTest
@ActiveProfiles("local")
class ActuatorSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("OPS-01 Test 1: Unauthenticated GET /actuator/health returns 200 OK with status UP")
    void testPublicHealthEndpointAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("OPS-01 Test 2: Unauthenticated /actuator/health response never leaks components, details, or sensitive metadata")
    void testHealthResponseSanitization() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.db").doesNotExist())
                .andExpect(jsonPath("$.diskSpace").doesNotExist())
                .andExpect(jsonPath("$.mail").doesNotExist())
                .andExpect(jsonPath("$.mailHealthIndicator").doesNotExist());
    }

    @Test
    @DisplayName("OPS-01 Test 3a: Unauthenticated GET /actuator/health/liveness returns 200 OK with status UP")
    void testLivenessProbeEndpointAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("OPS-01 Test 3b: Unauthenticated GET /actuator/health/readiness returns 200 OK with status UP")
    void testReadinessProbeEndpointAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("OPS-01 Test 4: Unauthenticated GET to sensitive Actuator endpoints is rejected (403 Forbidden)")
    void testSensitiveActuatorEndpointsRejectedForUnauthenticated() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/loggers"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/threaddump"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPS-01 Test 5: Application API endpoints remain strictly protected (403 Forbidden without token)")
    void testApplicationApisRemainProtected() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/monthly-comparison?month=5&year=2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OPS-01 Test 6: Public authentication endpoints remain accessible without token")
    void testPublicAuthEndpointsRemainAccessible() throws Exception {
        // Validation fails at controller layer, proving request bypassed security filter chain
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Login with credentials reaches auth service and returns 401 from BadCredentialsException handler
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@example.com\",\"password\":\"wrongpassword123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("OPS-01 Test 7: Production configuration defines minimal and safe Actuator properties")
    void testProductionActuatorPropertiesSafety() throws Exception {
        org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("application.properties");
        java.util.Properties props = org.springframework.core.io.support.PropertiesLoaderUtils.loadProperties(resource);

        org.assertj.core.api.Assertions.assertThat(props.getProperty("server.port"))
                .isEqualTo("${PORT:8080}");
        org.assertj.core.api.Assertions.assertThat(context.getEnvironment().getProperty("server.port"))
                .isEqualTo("8080");
        org.assertj.core.api.Assertions.assertThat(props.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        org.assertj.core.api.Assertions.assertThat(props.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo("true");
        org.assertj.core.api.Assertions.assertThat(props.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
        org.assertj.core.api.Assertions.assertThat(props.getProperty("management.endpoint.health.show-details"))
                .isEqualTo("never");
        org.assertj.core.api.Assertions.assertThat(props.getProperty("management.endpoint.health.show-components"))
                .isEqualTo("never");
        org.assertj.core.api.Assertions.assertThat(props.getProperty("management.health.mail.enabled"))
                .isEqualTo("false");
    }

    @Test
    @DisplayName("OPS-01 Test 8: Readiness group includes db and readinessState; liveness excludes db")
    void testHealthGroupContributorMembership() {
        org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups groups =
                context.getBean(org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups.class);

        org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup readiness = groups.get("readiness");
        org.assertj.core.api.Assertions.assertThat(readiness).isNotNull();
        org.assertj.core.api.Assertions.assertThat(readiness.isMember("db")).isTrue();
        org.assertj.core.api.Assertions.assertThat(readiness.isMember("readinessState")).isTrue();
        org.assertj.core.api.Assertions.assertThat(readiness.isMember("diskSpace")).isFalse();

        org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup liveness = groups.get("liveness");
        org.assertj.core.api.Assertions.assertThat(liveness).isNotNull();
        org.assertj.core.api.Assertions.assertThat(liveness.isMember("db")).isFalse();
        org.assertj.core.api.Assertions.assertThat(liveness.isMember("livenessState")).isTrue();
    }

    @Test
    @DisplayName("OPS-01 Test 9: Readiness returns 503 DOWN when database is unavailable; liveness remains 200 UP")
    void testReadinessDownWhenDatabaseUnavailable() throws Exception {
        org.springframework.boot.health.registry.HealthContributorRegistry registry =
                context.getBean(org.springframework.boot.health.registry.HealthContributorRegistry.class);
        org.springframework.boot.health.contributor.HealthContributor originalDb = registry.getContributor("db");

        try {
            // Simulate database failure by replacing db contributor with a DOWN indicator
            registry.unregisterContributor("db");
            registry.registerContributor("db", (org.springframework.boot.health.contributor.HealthIndicator) () ->
                    org.springframework.boot.health.contributor.Health.down()
                            .withDetail("secret_db_error", "FATAL: connection to database failed")
                            .build());

            // 1. Readiness probe must report DOWN (HTTP 503 Service Unavailable)
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status", is("DOWN")))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(jsonPath("$.secret_db_error").doesNotExist());

            // 2. Liveness probe must remain unaffected (HTTP 200 OK, UP) to prevent container restart loops
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("UP")))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());

            // 3. Overall health must report DOWN (HTTP 503) with sanitized output
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status", is("DOWN")))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(jsonPath("$.secret_db_error").doesNotExist());
        } finally {
            // Restore original database health contributor
            registry.unregisterContributor("db");
            if (originalDb != null) {
                registry.registerContributor("db", originalDb);
            }
        }
    }
}
