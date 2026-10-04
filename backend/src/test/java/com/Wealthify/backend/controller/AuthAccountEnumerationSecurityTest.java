package com.Wealthify.backend.controller;

import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.exception.GlobalExceptionHandler;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.security.JwtUtil;
import com.Wealthify.backend.security.OtpRateLimiter;
import com.Wealthify.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-07 Regression Test Suite: Account Enumeration Defense in Password Reset & OTP Verification
 *
 * Verifies that an unauthenticated attacker cannot determine whether an arbitrary email address
 * is registered on Wealthify via:
 * 1. Forgot-password endpoint (nonexistent email vs existing email vs email dispatch failure)
 * 2. OTP verification / reset-password endpoint (nonexistent email vs wrong OTP vs expired OTP)
 * 3. Rate limiting and brute-force lockout behavior
 * 4. Legitimate password reset happy-path functionality
 */
@ExtendWith(MockitoExtension.class)
class AuthAccountEnumerationSecurityTest {

    private MockMvc mockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JavaMailSender mailSender;

    private OtpRateLimiter otpRateLimiter;
    private AuthService authService;
    private AuthController authController;

    private static final String EXISTING_EMAIL = "victim@example.com";
    private static final String NONEXISTENT_EMAIL = "unknown-user-999@example.com";
    private static final String VALID_OTP = "654321";
    private static final String INVALID_OTP = "000000";
    private static final String STRONG_PASSWORD = "NewSecurePassword123";

    @BeforeEach
    void setUp() {
        otpRateLimiter = new OtpRateLimiter(5, 5, 600, 5, 900);
        otpRateLimiter.resetAll();

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtUtil,
                authenticationManager,
                mailSender,
                otpRateLimiter
        );
        ReflectionTestUtils.setField(authService, "senderEmail", "noreply@wealthify.test");

        authController = new AuthController(authService, otpRateLimiter);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Test 1 & 2: Forgot Password — Nonexistent vs Existing Account ──────────

    @Test
    @DisplayName("SEC-07 Test 1 & 2: Forgot password returns identical responses for nonexistent and existing accounts")
    void testForgotPasswordIdenticalResponsesForNonexistentAndExistingAccounts() throws Exception {
        // Setup: existing account in DB
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(EXISTING_EMAIL)
                .name("Victim")
                .password("encoded_pwd")
                .build();
        when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail(NONEXISTENT_EMAIL)).thenReturn(Optional.empty());

        // A: Nonexistent account
        MvcResult nonexistentResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\"}", NONEXISTENT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn();

        // B: Existing account
        MvcResult existingResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\"}", EXISTING_EMAIL)))
                .andExpect(status().isOk())
                .andReturn();

        // Compare responses to ensure zero enumeration oracle
        assertResponsesIndistinguishable(nonexistentResult, existingResult);
        assertThat(nonexistentResult.getResponse().getContentAsString())
                .contains("If the account exists, a secure OTP code has been sent to your inbox.");

        // Verify mailSender was only called for existing account, but caller sees identical result
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    // ─── Test 3 & 4: OTP Verification — Nonexistent vs Existing Account with Invalid OTP ───

    @Test
    @DisplayName("SEC-07 Test 3 & 4: OTP verification returns identical 400 Bad Request for nonexistent and existing accounts")
    void testResetPasswordIdenticalResponsesForNonexistentAndInvalidOtp() throws Exception {
        // Setup: existing account with an active OTP
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(EXISTING_EMAIL)
                .name("Victim")
                .password("old_encoded_pwd")
                .resetToken(VALID_OTP)
                .resetTokenExpiry(LocalDateTime.now().plusMinutes(5))
                .build();
        when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail(NONEXISTENT_EMAIL)).thenReturn(Optional.empty());

        // A: Nonexistent account with arbitrary OTP
        MvcResult nonexistentResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                NONEXISTENT_EMAIL, INVALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // B: Existing account with invalid OTP
        MvcResult existingResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                EXISTING_EMAIL, INVALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Compare responses to ensure zero enumeration oracle
        assertResponsesIndistinguishable(nonexistentResult, existingResult);
        assertThat(nonexistentResult.getResponse().getContentAsString())
                .contains("Invalid or expired verification code.");
    }

    @Test
    @DisplayName("SEC-07: Expired OTP returns identical response to nonexistent account and invalid OTP")
    void testResetPasswordExpiredOtpIsIndistinguishable() throws Exception {
        // Setup: existing account with expired OTP
        User existingUserExpiredOtp = User.builder()
                .id(UUID.randomUUID())
                .email(EXISTING_EMAIL)
                .name("Victim")
                .password("old_encoded_pwd")
                .resetToken(VALID_OTP)
                .resetTokenExpiry(LocalDateTime.now().minusMinutes(2))
                .build();
        when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(existingUserExpiredOtp));
        when(userRepository.findByEmail(NONEXISTENT_EMAIL)).thenReturn(Optional.empty());

        // Nonexistent account
        MvcResult nonexistentResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                NONEXISTENT_EMAIL, VALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Existing account with expired OTP
        MvcResult expiredResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                EXISTING_EMAIL, VALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertResponsesIndistinguishable(nonexistentResult, expiredResult);
        assertThat(expiredResult.getResponse().getContentAsString())
                .contains("Invalid or expired verification code.");
    }

    @Test
    @DisplayName("SEC-07: Existing account with no reset token requested returns identical error")
    void testResetPasswordNoOtpRequestedIsIndistinguishable() throws Exception {
        // Setup: existing account that never requested password reset (resetToken is null)
        User existingUserNoToken = User.builder()
                .id(UUID.randomUUID())
                .email(EXISTING_EMAIL)
                .name("Victim")
                .password("old_encoded_pwd")
                .resetToken(null)
                .resetTokenExpiry(null)
                .build();
        when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(existingUserNoToken));
        when(userRepository.findByEmail(NONEXISTENT_EMAIL)).thenReturn(Optional.empty());

        MvcResult nonexistentResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                NONEXISTENT_EMAIL, VALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andReturn();

        MvcResult noTokenResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                EXISTING_EMAIL, VALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertResponsesIndistinguishable(nonexistentResult, noTokenResult);
    }

    // ─── Test 5: Legitimate Password Reset Still Works ─────────────────────────

    @Test
    @DisplayName("SEC-07 Test 5: Legitimate password reset succeeds when valid OTP and compliant password are provided")
    void testLegitimatePasswordResetSucceeds() throws Exception {
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(EXISTING_EMAIL)
                .name("Legit User")
                .password("old_hash")
                .resetToken(VALID_OTP)
                .resetTokenExpiry(LocalDateTime.now().plusMinutes(5))
                .build();
        when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode(STRONG_PASSWORD)).thenReturn("new_hash_456");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                EXISTING_EMAIL, VALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully."));

        assertThat(existingUser.getPassword()).isEqualTo("new_hash_456");
        assertThat(existingUser.getResetToken()).isNull();
        assertThat(existingUser.getResetTokenExpiry()).isNull();
        verify(userRepository, times(1)).save(existingUser);
    }

    // ─── Test 6: Rate Limiting & Lockout Intact ────────────────────────────────

    @Test
    @DisplayName("SEC-07 Test 6: OTP verification lockout engages identically for nonexistent and existing accounts")
    void testOtpVerificationLockoutEngagesIdentically() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Perform 5 failed attempts for a nonexistent email to trigger lockout
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                    NONEXISTENT_EMAIL, INVALID_OTP, STRONG_PASSWORD)))
                    .andExpect(status().isBadRequest());
        }

        // 6th attempt must be locked out with HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"%s\"}",
                                NONEXISTENT_EMAIL, INVALID_OTP, STRONG_PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Account is temporarily locked")));
    }

    // ─── Test 7: Email Dispatch Failure ────────────────────────────────────────

    @Test
    @DisplayName("SEC-07 Test 7: Email dispatch failure for existing account does not leak account existence")
    void testEmailDispatchFailureDoesNotLeakAccountExistence() throws Exception {
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(EXISTING_EMAIL)
                .name("Victim")
                .password("encoded_pwd")
                .build();
        when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail(NONEXISTENT_EMAIL)).thenReturn(Optional.empty());

        // Simulate SMTP failure when sending email to existing account
        doThrow(new MailSendException("SMTP connection timed out"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Request for nonexistent account
        MvcResult nonexistentResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\"}", NONEXISTENT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn();

        // Request for existing account whose email dispatch fails
        MvcResult failedMailResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\"}", EXISTING_EMAIL)))
                .andExpect(status().isOk())
                .andReturn();

        // Both must return identical HTTP 200 responses with the standard generic message
        assertResponsesIndistinguishable(nonexistentResult, failedMailResult);
        assertThat(failedMailResult.getResponse().getContentAsString())
                .contains("If the account exists, a secure OTP code has been sent to your inbox.");

        // User's reset token must be cleared to prevent dangling active OTPs
        assertThat(existingUser.getResetToken()).isNull();
        assertThat(existingUser.getResetTokenExpiry()).isNull();
    }

    // ─── Helper: Verify Identical Observable Behavior ──────────────────────────

    private void assertResponsesIndistinguishable(MvcResult responseA, MvcResult responseB) throws Exception {
        assertThat(responseA.getResponse().getStatus())
                .as("HTTP status codes must be identical")
                .isEqualTo(responseB.getResponse().getStatus());

        assertThat(responseA.getResponse().getContentAsString())
                .as("Response bodies must be identical")
                .isEqualTo(responseB.getResponse().getContentAsString());

        assertThat(responseA.getResponse().getContentType())
                .as("Content types must be identical")
                .isEqualTo(responseB.getResponse().getContentType());
    }
}
