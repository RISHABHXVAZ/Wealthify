package com.Wealthify.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private String secureSecret64Bytes;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        secureSecret64Bytes = Base64.getEncoder().encodeToString(randomBytes);

        jwtUtil = new JwtUtil(secureSecret64Bytes, 3600000L); // 1 hour
    }

    @Test
    @DisplayName("Should fail fast if secret is null, empty, or whitespace")
    void testFailFastWhenSecretMissing() {
        JwtUtil nullSecretUtil = new JwtUtil();
        nullSecretUtil.setSecret(null);
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, nullSecretUtil::validateSecret);
        assertTrue(ex1.getMessage().contains("missing or empty"));

        JwtUtil emptySecretUtil = new JwtUtil();
        emptySecretUtil.setSecret("   ");
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, emptySecretUtil::validateSecret);
        assertTrue(ex2.getMessage().contains("missing or empty"));
    }

    @Test
    @DisplayName("Should fail fast if secret has less than 256 bits (32 bytes)")
    void testFailFastWhenSecretTooShort() {
        JwtUtil shortSecretUtil = new JwtUtil();
        shortSecretUtil.setSecret("too-short-secret");
        IllegalStateException ex = assertThrows(IllegalStateException.class, shortSecretUtil::validateSecret);
        assertTrue(ex.getMessage().contains("at least 256 bits"));
    }

    @Test
    @DisplayName("Newly issued JWT should be valid and subject email correctly extracted")
    void testGenerateTokenAndValidate() {
        String email = "student@wealthify.test";
        String token = jwtUtil.generateToken(email);

        assertNotNull(token);
        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals(email, jwtUtil.extractEmail(token));
    }

    @Test
    @DisplayName("Token signed with different secret should be rejected")
    void testInvalidSignatureRejected() {
        byte[] otherRandomBytes = new byte[64];
        new SecureRandom().nextBytes(otherRandomBytes);
        String otherSecret = Base64.getEncoder().encodeToString(otherRandomBytes);

        JwtUtil otherJwtUtil = new JwtUtil(otherSecret, 3600000L);
        String tokenFromOther = otherJwtUtil.generateToken("student@wealthify.test");

        // Attempt validation with jwtUtil (different key)
        assertFalse(jwtUtil.isTokenValid(tokenFromOther));
    }

    @Test
    @DisplayName("Tampered token payload or signature should be rejected")
    void testTamperedTokenRejected() {
        String token = jwtUtil.generateToken("student@wealthify.test");
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertFalse(jwtUtil.isTokenValid(tamperedToken));
    }

    @Test
    @DisplayName("Null or blank token should return false safely without throwing")
    void testBlankTokenHandledGracefully() {
        assertFalse(jwtUtil.isTokenValid(null));
        assertFalse(jwtUtil.isTokenValid(""));
        assertFalse(jwtUtil.isTokenValid("   "));
        assertFalse(jwtUtil.isTokenValid("not.a.valid.jwt"));
    }

    @Test
    @DisplayName("Expired JWT should be rejected")
    void testExpiredTokenRejected() {
        // Expiration in the past (-10 seconds)
        JwtUtil expiredUtil = new JwtUtil(secureSecret64Bytes, -10000L);
        String expiredToken = expiredUtil.generateToken("student@wealthify.test");

        assertFalse(jwtUtil.isTokenValid(expiredToken));
    }

    @Test
    @DisplayName("Restarting application with same configured secret keeps existing JWT valid")
    void testSameSecretKeepsTokenValidAcrossInstances() {
        String token = jwtUtil.generateToken("user@wealthify.test");

        // Simulate restarted service with identical secret configuration
        JwtUtil restartedServiceUtil = new JwtUtil(secureSecret64Bytes, 3600000L);
        assertTrue(restartedServiceUtil.isTokenValid(token));
        assertEquals("user@wealthify.test", restartedServiceUtil.extractEmail(token));
    }

    @Test
    @DisplayName("Rotating/changing secret invalidates previously issued JWTs")
    void testChangingSecretInvalidatesOldJwt() {
        String token = jwtUtil.generateToken("user@wealthify.test");
        assertTrue(jwtUtil.isTokenValid(token));

        // Rotate secret to new random 64-byte key
        byte[] rotatedBytes = new byte[64];
        new SecureRandom().nextBytes(rotatedBytes);
        String rotatedSecret = Base64.getEncoder().encodeToString(rotatedBytes);

        JwtUtil rotatedJwtUtil = new JwtUtil(rotatedSecret, 3600000L);
        assertFalse(rotatedJwtUtil.isTokenValid(token));
    }
}
