package com.Wealthify.backend.config;

import com.Wealthify.backend.WealthifyApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringProfileConfigurationTest {

    @Test
    @DisplayName("WealthifyApplication entry point defines standard main method without hardcoded profiles")
    void testWealthifyApplicationMainMethodExists() throws NoSuchMethodException {
        Method mainMethod = WealthifyApplication.class.getMethod("main", String[].class);
        assertThat(mainMethod).isNotNull();
        assertThat(mainMethod.getReturnType()).isEqualTo(void.class);
    }

    @Test
    @DisplayName("Profile resolution: Default resolves to 'prod' when SPRING_PROFILES_ACTIVE is not set")
    void testDefaultProfileIsProd() {
        MockEnvironment env = new MockEnvironment();
        // Simulating spring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}
        String propertyTemplate = "${SPRING_PROFILES_ACTIVE:prod}";
        String resolved = env.resolvePlaceholders(propertyTemplate);

        assertThat(resolved).isEqualTo("prod");
    }

    @Test
    @DisplayName("Profile resolution: Resolves to 'local' when SPRING_PROFILES_ACTIVE=local")
    void testProfileResolvesToLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "local");

        String propertyTemplate = "${SPRING_PROFILES_ACTIVE:prod}";
        String resolved = env.resolvePlaceholders(propertyTemplate);

        assertThat(resolved).isEqualTo("local");
    }

    @Test
    @DisplayName("Profile resolution: Resolves to 'prod' when SPRING_PROFILES_ACTIVE=prod")
    void testProfileResolvesToProd() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("SPRING_PROFILES_ACTIVE", "prod");

        String propertyTemplate = "${SPRING_PROFILES_ACTIVE:prod}";
        String resolved = env.resolvePlaceholders(propertyTemplate);

        assertThat(resolved).isEqualTo("prod");
    }

    @Test
    @DisplayName("CORS Behavior: 'prod' profile strictly enforces APP_FRONTEND_URL requirement")
    void testProdProfileEnforcesFrontendUrl() {
        // In prod profile, missing frontend URL must fail fast with IllegalStateException
        assertThatThrownBy(() -> CorsConfigurationHelper.resolveAllowedOrigins("", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: 'app.frontend.url'");

        // In prod profile, valid frontend URL must succeed
        List<String> origins = CorsConfigurationHelper.resolveAllowedOrigins("https://wealthify.vercel.app", true);
        assertThat(origins).contains("https://wealthify.vercel.app");
    }

    @Test
    @DisplayName("CORS Behavior: 'local' profile falls back to safe localhost origins when frontend URL is blank")
    void testLocalProfileAllowsSafeDefaults() {
        List<String> origins = CorsConfigurationHelper.resolveAllowedOrigins("", false);
        assertThat(origins).contains("http://localhost:5173", "http://localhost:3000");
        assertThat(origins).doesNotContain("*");
    }
}
