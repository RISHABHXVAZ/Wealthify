package com.Wealthify.backend.config;

import com.Wealthify.backend.security.JwtFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CorsSecurityIntegrationTest {

    @Mock
    private JwtFilter jwtFilter;

    @RestController
    static class DummyTestController {
        @GetMapping("/api/test-cors")
        public String testCors() {
            return "ok";
        }
    }

    @Test
    @DisplayName("Production: SecurityConfig fails startup with IllegalStateException when APP_FRONTEND_URL is missing")
    void testProductionStartupFailsWhenOriginMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfig config = new SecurityConfig(jwtFilter, env);
        ReflectionTestUtils.setField(config, "frontendUrl", "");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: 'app.frontend.url'");
    }

    @Test
    @DisplayName("Production: SecurityConfig fails startup with IllegalStateException when APP_FRONTEND_URL is null")
    void testProductionStartupFailsWhenOriginNull() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfig config = new SecurityConfig(jwtFilter, env);
        ReflectionTestUtils.setField(config, "frontendUrl", null);

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: 'app.frontend.url'");
    }

    @Test
    @DisplayName("Production: SecurityConfig fails startup with IllegalArgumentException when APP_FRONTEND_URL is malformed")
    void testProductionStartupFailsWhenOriginMalformed() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfig config = new SecurityConfig(jwtFilter, env);
        ReflectionTestUtils.setField(config, "frontendUrl", "ftp://invalid-scheme.com");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use http or https scheme");
    }

    @Test
    @DisplayName("Production: SecurityConfig initializes correctly when valid APP_FRONTEND_URL is provided")
    void testProductionStartupSucceedsWithValidOrigin() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        SecurityConfig config = new SecurityConfig(jwtFilter, env);
        ReflectionTestUtils.setField(config, "frontendUrl", "https://wealthify.vercel.app/");

        CorsConfigurationSource source = config.corsConfigurationSource();
        assertThat(source).isNotNull();
    }

    @Test
    @DisplayName("Local: SecurityConfig initializes with safe localhost defaults when APP_FRONTEND_URL is omitted")
    void testLocalStartupSucceedsWithSafeDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        SecurityConfig config = new SecurityConfig(jwtFilter, env);
        ReflectionTestUtils.setField(config, "frontendUrl", "");

        CorsConfigurationSource source = config.corsConfigurationSource();
        assertThat(source).isNotNull();
    }

    @Test
    @DisplayName("CORS Filter: Allowed origin is accepted with credentials in preflight OPTIONS request")
    void testAllowedOriginAcceptedInPreflight() throws Exception {
        List<String> allowedOrigins = List.of("http://localhost:5173", "https://wealthify.vercel.app");
        CorsConfigurationSource source = request -> CorsConfigurationHelper.buildCorsConfiguration(allowedOrigins);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummyTestController())
                .addFilter(new CorsFilter(source))
                .build();

        mockMvc.perform(options("/api/test-cors")
                        .header("Origin", "https://wealthify.vercel.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://wealthify.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    @DisplayName("CORS Filter: Disallowed origin is rejected (403 Forbidden) in preflight OPTIONS request")
    void testDisallowedOriginRejectedInPreflight() throws Exception {
        List<String> allowedOrigins = List.of("http://localhost:5173", "https://wealthify.vercel.app");
        CorsConfigurationSource source = request -> CorsConfigurationHelper.buildCorsConfiguration(allowedOrigins);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummyTestController())
                .addFilter(new CorsFilter(source))
                .build();

        mockMvc.perform(options("/api/test-cors")
                        .header("Origin", "https://malicious-attacker.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CORS Filter: Simple request with disallowed origin does not receive Access-Control-Allow-Origin header")
    void testDisallowedOriginSimpleRequestNotAllowed() throws Exception {
        List<String> allowedOrigins = List.of("http://localhost:5173", "https://wealthify.vercel.app");
        CorsConfigurationSource source = request -> CorsConfigurationHelper.buildCorsConfiguration(allowedOrigins);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DummyTestController())
                .addFilter(new CorsFilter(source))
                .build();

        mockMvc.perform(get("/api/test-cors")
                        .header("Origin", "https://malicious-attacker.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
