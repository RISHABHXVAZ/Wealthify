package com.Wealthify.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEP-03 Regression Test Suite:
 * Verifies production database configuration hardening, ensuring:
 * 1. Production defaults to ddl-auto=validate (no automatic schema mutation).
 * 2. Production disables SQL logging (show-sql=false, format_sql=false).
 * 3. Local profile retains developer convenience (ddl-auto=update, show-sql=true).
 * 4. Production profile does not inherit local development database settings.
 */
class DatabaseProfileConfigurationTest {

    private Properties loadProperties(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return new Properties();
        }
        return PropertiesLoaderUtils.loadProperties(resource);
    }

    @Test
    @DisplayName("DEP-03 Test 1: Production configuration disables automatic DDL and SQL logging")
    void testProductionConfigurationHardening() throws IOException {
        Properties prodProps = loadProperties("application.properties");
        StandardEnvironment env = new StandardEnvironment();

        Map<String, Object> map = new HashMap<>();
        prodProps.forEach((k, v) -> map.put((String) k, v));
        env.getPropertySources().addLast(new MapPropertySource("prod", map));

        // 1. ddl-auto must resolve to 'validate' (not 'update', 'create', or 'create-drop')
        String ddlAuto = env.resolvePlaceholders(prodProps.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertThat(ddlAuto).isEqualTo("validate");
        assertThat(ddlAuto).isNotEqualTo("update");
        assertThat(ddlAuto).isNotEqualTo("create");
        assertThat(ddlAuto).isNotEqualTo("create-drop");

        // 2. show-sql must resolve to 'false'
        String showSql = env.resolvePlaceholders(prodProps.getProperty("spring.jpa.show-sql"));
        assertThat(showSql).isEqualTo("false");

        // 3. format_sql must resolve to 'false'
        String formatSql = env.resolvePlaceholders(prodProps.getProperty("spring.jpa.properties.hibernate.format_sql"));
        assertThat(formatSql).isEqualTo("false");
    }

    @Test
    @DisplayName("DEP-03 Test 2: Local development profile retains ddl-auto=update and show-sql=true")
    void testLocalConfigurationDeveloperConvenience() throws IOException {
        Properties localProps = loadProperties("application-local.properties");

        // Local development must have explicit developer overrides
        assertThat(localProps.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(localProps.getProperty("spring.jpa.show-sql")).isEqualTo("true");
        assertThat(localProps.getProperty("spring.jpa.properties.hibernate.format_sql")).isEqualTo("true");
    }

    @Test
    @DisplayName("DEP-03 Test 3: Profile separation — prod does not inherit local JPA settings")
    void testProfileSeparation() throws IOException {
        Properties prodProps = loadProperties("application.properties");
        Properties localProps = loadProperties("application-local.properties");

        StandardEnvironment prodEnv = new StandardEnvironment();
        Map<String, Object> prodMap = new HashMap<>();
        prodProps.forEach((k, v) -> prodMap.put((String) k, v));
        prodEnv.getPropertySources().addLast(new MapPropertySource("prod", prodMap));

        // When in prod (localProps NOT in environment):
        assertThat(prodEnv.resolvePlaceholders(prodProps.getProperty("spring.jpa.hibernate.ddl-auto")))
                .isEqualTo("validate");
        assertThat(prodEnv.resolvePlaceholders(prodProps.getProperty("spring.jpa.show-sql")))
                .isEqualTo("false");

        // When in local (localProps layered on top of prodProps):
        StandardEnvironment localEnv = new StandardEnvironment();
        Map<String, Object> localMap = new HashMap<>();
        localProps.forEach((k, v) -> localMap.put((String) k, v));
        localEnv.getPropertySources().addLast(new MapPropertySource("prod", prodMap));
        localEnv.getPropertySources().addFirst(new MapPropertySource("local", localMap));

        assertThat(localEnv.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(localEnv.getProperty("spring.jpa.show-sql")).isEqualTo("true");
        assertThat(localEnv.getProperty("spring.jpa.properties.hibernate.format_sql")).isEqualTo("true");
    }

    @Test
    @DisplayName("DEP-03 Test 4: Production environment variables can safely override JPA settings if required")
    void testProductionEnvironmentVariableOverrides() throws IOException {
        Properties prodProps = loadProperties("application.properties");
        StandardEnvironment env = new StandardEnvironment();

        // Simulate operator providing custom env vars in production via property source
        Map<String, Object> envOverrides = Map.of(
                "SPRING_JPA_HIBERNATE_DDL_AUTO", "none",
                "SPRING_JPA_SHOW_SQL", "false"
        );
        env.getPropertySources().addFirst(new MapPropertySource("systemEnvironmentOverrides", envOverrides));

        Map<String, Object> map = new HashMap<>();
        prodProps.forEach((k, v) -> map.put((String) k, v));
        env.getPropertySources().addLast(new MapPropertySource("prod", map));

        assertThat(env.resolvePlaceholders(prodProps.getProperty("spring.jpa.hibernate.ddl-auto")))
                .isEqualTo("none");
        assertThat(env.resolvePlaceholders(prodProps.getProperty("spring.jpa.show-sql")))
                .isEqualTo("false");
    }
}
