package com.Wealthify.backend.controller;

import com.Wealthify.backend.exception.GlobalExceptionHandler;
import com.Wealthify.backend.exception.RateLimitExceededException;
import com.Wealthify.backend.security.OtpRateLimiter;
import com.Wealthify.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerOtpSecurityTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    private OtpRateLimiter otpRateLimiter;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        // 3 requests allowed per 10 minutes, 5 verify attempts allowed
        otpRateLimiter = new OtpRateLimiter(3, 3, 600, 5, 900);
        authController = new AuthController(authService, otpRateLimiter);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password: First request succeeds with 200 OK and generic message")
    void testForgotPasswordSuccess() throws Exception {
        doNothing().when(authService).processForgotPassword("student@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists, a secure OTP code has been sent to your inbox."));

        verify(authService, times(1)).processForgotPassword("student@example.com");
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password: Enforces rate limit and returns HTTP 429 with Retry-After header")
    void testForgotPasswordRateLimitingReturns429WithRetryAfter() throws Exception {
        doNothing().when(authService).processForgotPassword(anyString());

        // Perform 3 allowed requests from IP 203.0.113.1
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .header("X-Forwarded-For", "203.0.113.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"student@example.com\"}"))
                    .andExpect(status().isOk());
        }

        // 4th request must be rejected with 429 Too Many Requests and Retry-After header
        mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many password reset requests")));

        // Service should only have been called 3 times, 4th was blocked before reaching service
        verify(authService, times(3)).processForgotPassword("student@example.com");
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password: Same email attacked from different IPs still enforces rate limit")
    void testForgotPasswordEmailLimitBlocksAcrossDifferentIps() throws Exception {
        doNothing().when(authService).processForgotPassword(anyString());

        // Send 3 requests targeting the same email from 3 different IPs
        mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "198.51.100.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"target@example.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "198.51.100.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"target@example.com\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "198.51.100.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"target@example.com\"}"))
                .andExpect(status().isOk());

        // 4th request targeting same email from yet another IP (198.51.100.4) is blocked
        mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "198.51.100.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"target@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many password reset requests")));
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password: Non-existent email returns same response and enforces rate limit without leaking existence")
    void testForgotPasswordNoAccountEnumeration() throws Exception {
        doNothing().when(authService).processForgotPassword("nonexistent@example.com");

        // Requesting for non-existent email returns identical 200 response
        mockMvc.perform(post("/api/auth/forgot-password")
                        .header("X-Forwarded-For", "192.0.2.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists, a secure OTP code has been sent to your inbox."));
    }

    @Test
    @DisplayName("POST /api/auth/reset-password: Lockout returns HTTP 429 with Retry-After header")
    void testResetPasswordLockedOutReturns429() throws Exception {
        when(authService.verifyOtpAndResetPassword(eq("locked@example.com"), anyString(), anyString()))
                .thenThrow(new RateLimitExceededException("Too many failed verification attempts. Account is temporarily locked. Please try again in 15 minutes.", 900));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"locked@example.com\",\"otp\":\"123456\",\"newPassword\":\"newSecret123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Account is temporarily locked")));
    }
}
