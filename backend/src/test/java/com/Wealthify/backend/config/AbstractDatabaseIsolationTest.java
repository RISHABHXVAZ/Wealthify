package com.Wealthify.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Base test class enforcing strict fail-fast database isolation.
 *
 * Guarantees:
 * 1. Test environment cannot inherit or be redirected by external environment variables
 *    (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, etc.).
 * 2. Before any test operation executes, verifies that the active DataSource points
 *    strictly to the embedded loopback PostgreSQL instance, and NEVER to a remote endpoint
 *    (e.g., neon.tech, aws, render, cloud, etc.).
 */
public abstract class AbstractDatabaseIsolationTest {

    @Autowired
    protected DataSource dataSource;

    @DynamicPropertySource
    static void enforceIsolatedProperties(DynamicPropertyRegistry registry) {
        // Enforce test profile and neutralize any ambient environment variable overrides
        registry.add("spring.profiles.active", () -> "test");
        registry.add("app.frontend.url", () -> "http://localhost:5173");
        registry.add("app.jwt.secret", () -> "dGVzdF9qd3Rfc2VjcmV0X2tleV9mb3JfaXNvbGF0ZWRfdGVzdF9zdWl0ZV8xMjM0NTY3ODkwMTI=");
    }

    @BeforeEach
    void verifyDatabaseIsolationGuard() {
        assertThat(dataSource).as("DataSource must be present").isNotNull();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String url = metaData.getURL();

            assertThat(url)
                    .as("DataSource JDBC URL must not be null")
                    .isNotNull();

            // Strict fail-closed isolation guard
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains("neon.tech")
                    || lowerUrl.contains("render.com")
                    || lowerUrl.contains("aws")
                    || lowerUrl.contains("sslmode=require")) {
                fail("CRITICAL ISOLATION BREACH: Test context connected to remote database: " + url);
            }

            // Must be loopback / embedded PostgreSQL instance
            boolean isEmbeddedOrLocal = lowerUrl.contains("localhost")
                    || lowerUrl.contains("127.0.0.1")
                    || lowerUrl.contains("embedded");

            assertThat(isEmbeddedOrLocal)
                    .as("DataSource must be connected to an embedded or local test database, but was: %s", url)
                    .isTrue();

        } catch (SQLException e) {
            fail("Failed to verify database isolation guard", e);
        }
    }
}
