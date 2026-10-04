package com.Wealthify.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigurationHelperTest {

    @Test
    @DisplayName("Valid http and https origins normalized properly")
    void testValidOriginsNormalized() {
        assertThat(CorsConfigurationHelper.validateAndNormalizeOrigin("https://wealthify.vercel.app/"))
                .isEqualTo("https://wealthify.vercel.app");

        assertThat(CorsConfigurationHelper.validateAndNormalizeOrigin("http://localhost:5173///"))
                .isEqualTo("http://localhost:5173");

        assertThat(CorsConfigurationHelper.validateAndNormalizeOrigin("HTTPS://MY-APP.COM:8443"))
                .isEqualTo("https://my-app.com:8443");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Blank or empty origins rejected")
    void testBlankOriginsRejected(String blank) {
        assertThatThrownBy(() -> CorsConfigurationHelper.validateAndNormalizeOrigin(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Frontend origin URL must not be blank.");
    }

    @Test
    @DisplayName("Null origin rejected")
    void testNullOriginRejected() {
        assertThatThrownBy(() -> CorsConfigurationHelper.validateAndNormalizeOrigin(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Frontend origin URL must not be blank.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "null", "NULL"})
    @DisplayName("Wildcard or literal null origins rejected")
    void testWildcardAndNullRejected(String invalid) {
        assertThatThrownBy(() -> CorsConfigurationHelper.validateAndNormalizeOrigin(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wildcard or 'null' origins are not permitted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://files.example.com",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "data:text/html,test",
            "ws://localhost:8080"
    })
    @DisplayName("Non-http/https schemes rejected")
    void testInvalidSchemesRejected(String badScheme) {
        assertThatThrownBy(() -> CorsConfigurationHelper.validateAndNormalizeOrigin(badScheme))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use http or https scheme");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "wealthify.vercel.app", // missing scheme
            "http://",              // missing host
            "https:///test",        // missing host
            "http://:8080"          // missing host
    })
    @DisplayName("Malformed origin URLs missing scheme or host rejected")
    void testMalformedUrlsRejected(String malformed) {
        assertThatThrownBy(() -> CorsConfigurationHelper.validateAndNormalizeOrigin(malformed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://wealthify.vercel.app/api",
            "https://wealthify.vercel.app/dashboard/",
            "http://localhost:5173/expenses"
    })
    @DisplayName("Origins containing path components rejected")
    void testOriginsWithPathRejected(String originWithPath) {
        assertThatThrownBy(() -> CorsConfigurationHelper.validateAndNormalizeOrigin(originWithPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain a path component");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://wealthify.vercel.app?query=1",
            "http://localhost:5173#section"
    })
    @DisplayName("Origins containing query or fragment components rejected")
    void testOriginsWithQueryOrFragmentRejected(String queryOrigin) {
        assertThatThrownBy(() -> CorsConfigurationHelper.validateAndNormalizeOrigin(queryOrigin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain query or fragment components");
    }

    // ─── Production Resolution Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Production: Missing or blank frontend URL throws IllegalStateException (never NullPointerException)")
    void testProductionMissingOriginFailsCleanly() {
        assertThatThrownBy(() -> CorsConfigurationHelper.resolveAllowedOrigins(null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: 'app.frontend.url'");

        assertThatThrownBy(() -> CorsConfigurationHelper.resolveAllowedOrigins("", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: 'app.frontend.url'");

        assertThatThrownBy(() -> CorsConfigurationHelper.resolveAllowedOrigins("   ", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required configuration: 'app.frontend.url'");
    }

    @Test
    @DisplayName("Production: Valid frontend URL accepted and normalized without wildcards")
    void testProductionValidOriginAccepted() {
        List<String> origins = CorsConfigurationHelper.resolveAllowedOrigins("https://wealthify-app.vercel.app/", true);

        assertThat(origins).contains("https://wealthify-app.vercel.app");
        assertThat(origins).contains("http://localhost:5173");
        assertThat(origins).doesNotContain("*");
    }

    @Test
    @DisplayName("Production: Comma-separated multiple frontend URLs accepted")
    void testProductionMultipleOriginsAccepted() {
        List<String> origins = CorsConfigurationHelper.resolveAllowedOrigins(
                "https://app.wealthify.in, https://staging.wealthify.in/", true
        );

        assertThat(origins).contains("https://app.wealthify.in", "https://staging.wealthify.in");
        assertThat(origins).doesNotContain("*");
    }

    // ─── Non-Production (Local/Test) Resolution Tests ───────────────────────────

    @Test
    @DisplayName("Local/Test: Missing or blank frontend URL safely defaults to localhost without NPE or wildcard")
    void testLocalMissingOriginSafeDefaults() {
        List<String> originsNull = CorsConfigurationHelper.resolveAllowedOrigins(null, false);
        assertThat(originsNull).contains("http://localhost:5173", "http://localhost:3000");
        assertThat(originsNull).doesNotContain("*");

        List<String> originsBlank = CorsConfigurationHelper.resolveAllowedOrigins("", false);
        assertThat(originsBlank).contains("http://localhost:5173", "http://localhost:3000");
        assertThat(originsBlank).doesNotContain("*");
    }

    @Test
    @DisplayName("Local/Test: Custom local origin accepted alongside safe defaults")
    void testLocalCustomOriginAccepted() {
        List<String> origins = CorsConfigurationHelper.resolveAllowedOrigins("http://localhost:8081", false);
        assertThat(origins).contains("http://localhost:8081", "http://localhost:5173", "http://localhost:3000");
    }

    // ─── CorsConfiguration Contract Tests ──────────────────────────────────────

    @Test
    @DisplayName("CorsConfiguration sets correct methods, headers, and credentials")
    void testCorsConfigurationContract() {
        List<String> origins = List.of("https://wealthify-app.vercel.app");
        CorsConfiguration config = CorsConfigurationHelper.buildCorsConfiguration(origins);

        assertThat(config.getAllowedOrigins()).isEqualTo(origins);
        assertThat(config.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).containsExactly("*");
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }
}
