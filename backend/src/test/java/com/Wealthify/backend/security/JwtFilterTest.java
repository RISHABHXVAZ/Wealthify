package com.Wealthify.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtFilterTest {

    private String secureSecret64Bytes;
    private JwtUtil jwtUtil;
    private JwtFilter jwtFilter;
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        secureSecret64Bytes = Base64.getEncoder().encodeToString(randomBytes);

        jwtUtil = new JwtUtil(secureSecret64Bytes, 3600000L);

        userDetailsService = email -> new User(
                email,
                "encodedPassword",
                Collections.emptyList()
        );

        jwtFilter = new JwtFilter(jwtUtil, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Request without Authorization header leaves security context unauthenticated")
    void testRequestWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Request with valid JWT authenticated successfully in security context")
    void testRequestWithValidToken() throws Exception {
        String email = "verified.student@wealthify.test";
        String token = jwtUtil.generateToken(email);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(email, principal.getUsername());
    }

    @Test
    @DisplayName("Request with token signed with old revoked secret is rejected")
    void testRequestWithOldRevokedSecret() throws Exception {
        String oldSecret = "mySecretKey123456789012345678901234567890";
        String oldToken = Jwts.builder()
                .subject("student@wealthify.test")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(oldSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + oldToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Request with expired token is rejected and leaves security context unauthenticated")
    void testRequestWithExpiredToken() throws Exception {
        JwtUtil expiredUtil = new JwtUtil(secureSecret64Bytes, -5000L);
        String expiredToken = expiredUtil.generateToken("student@wealthify.test");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Request with invalid signature token is rejected")
    void testRequestWithInvalidSignatureToken() throws Exception {
        byte[] otherBytes = new byte[64];
        new SecureRandom().nextBytes(otherBytes);
        String otherSecret = Base64.getEncoder().encodeToString(otherBytes);

        String invalidSignatureToken = Jwts.builder()
                .subject("student@wealthify.test")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + invalidSignatureToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        jwtFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
