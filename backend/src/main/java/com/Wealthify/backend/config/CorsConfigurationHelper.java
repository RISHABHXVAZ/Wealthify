package com.Wealthify.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.cors.CorsConfiguration;

import java.net.URI;
import java.util.*;

/**
 * Defensive CORS configuration helper.
 *
 * Enforces:
 * 1. Explicit production frontend origin requirement (fails fast with IllegalStateException if missing/blank).
 * 2. Strict origin validation (rejects malformed, wildcard, path/query-bearing, or non-http/https schemes).
 * 3. Safe local/test fallback (permits standard localhost development ports without wildcards or NullPointerExceptions).
 * 4. Strict CORS parameters (restricted methods, credentials=true with explicit non-wildcard origins).
 */
@Slf4j
public final class CorsConfigurationHelper {

    public static final String DEFAULT_LOCAL_VITE_ORIGIN = "http://localhost:5173";
    public static final String DEFAULT_LOCAL_REACT_ORIGIN = "http://localhost:3000";

    private CorsConfigurationHelper() {}

    /**
     * Validates and normalizes a candidate origin string.
     *
     * @param origin Candidate origin URL (e.g. "https://wealthify.vercel.app/")
     * @return Normalized origin string (e.g. "https://wealthify.vercel.app")
     * @throws IllegalArgumentException if origin is blank, malformed, or invalid
     */
    public static String validateAndNormalizeOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("Frontend origin URL must not be blank.");
        }

        String trimmed = origin.trim();

        // Reject explicit wildcard or null literal
        if ("*".equals(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            throw new IllegalArgumentException("Wildcard or 'null' origins are not permitted: " + trimmed);
        }

        // Normalize trailing slashes
        String normalized = trimmed.replaceAll("/+$", "");

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed frontend origin URL: " + trimmed, ex);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Frontend origin must use http or https scheme: " + trimmed);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Frontend origin must contain a valid host: " + trimmed);
        }

        // Origins must not contain path components
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !path.equals("/")) {
            throw new IllegalArgumentException("Frontend origin must not contain a path component: " + trimmed);
        }

        // Origins must not contain query or fragment
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Frontend origin must not contain query or fragment components: " + trimmed);
        }

        int port = uri.getPort();
        if (port != -1) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Frontend origin port out of range: " + port);
            }
            return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
        } else {
            return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Resolves the list of allowed CORS origins based on raw configuration and environment.
     *
     * @param rawFrontendUrl Raw URL configuration property (may be comma-delimited)
     * @param isProduction True if running in production profile
     * @return List of normalized allowed origin strings
     */
    public static List<String> resolveAllowedOrigins(String rawFrontendUrl, boolean isProduction) {
        Set<String> origins = new LinkedHashSet<>();

        if (isProduction) {
            if (rawFrontendUrl == null || rawFrontendUrl.isBlank()) {
                throw new IllegalStateException(
                        "Missing required configuration: 'app.frontend.url' (or environment variable 'APP_FRONTEND_URL') "
                                + "must be configured with a valid origin in production."
                );
            }

            // In production, parse and validate configured origins
            String[] parts = rawFrontendUrl.split(",");
            for (String part : parts) {
                if (!part.isBlank()) {
                    origins.add(validateAndNormalizeOrigin(part));
                }
            }

            if (origins.isEmpty()) {
                throw new IllegalStateException(
                        "No valid frontend origins found in 'app.frontend.url' for production environment."
                );
            }

            // Retain local Vite dev origin for hybrid staging/testing if present
            origins.add(DEFAULT_LOCAL_VITE_ORIGIN);
        } else {
            // Local / Test environment
            if (rawFrontendUrl != null && !rawFrontendUrl.isBlank()) {
                String[] parts = rawFrontendUrl.split(",");
                for (String part : parts) {
                    if (!part.isBlank()) {
                        try {
                            origins.add(validateAndNormalizeOrigin(part));
                        } catch (IllegalArgumentException ex) {
                            log.warn("Invalid frontend origin in non-production configuration '{}': {}", part, ex.getMessage());
                            throw ex; // Maintain strict configuration validity
                        }
                    }
                }
            }

            // Ensure safe defaults exist for local development without wildcard
            origins.add(DEFAULT_LOCAL_VITE_ORIGIN);
            origins.add(DEFAULT_LOCAL_REACT_ORIGIN);
        }

        return List.copyOf(origins);
    }

    /**
     * Builds a Spring CorsConfiguration with strict security settings.
     *
     * @param allowedOrigins List of allowed origins
     * @return Configured CorsConfiguration
     */
    public static CorsConfiguration buildCorsConfiguration(List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }
}
