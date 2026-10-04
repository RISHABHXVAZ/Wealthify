package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.RegisterRequest;
import com.Wealthify.backend.dto.ResetPasswordRequest;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthPasswordSecurityTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

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
    private AuthController authController;

    @BeforeEach
    void setUp() {
        otpRateLimiter = new OtpRateLimiter(3, 3, 600, 5, 900);
        authController = new AuthController(authService, otpRateLimiter);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Registration Endpoint Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Registration: Valid password is accepted")
    void testRegisterValidPasswordAccepted() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn("User registered successfully");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"SecurePassword123\"}"))
                .andExpect(status().isOk());

        verify(authService, times(1)).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("Registration: Password exactly 8 characters accepted")
    void testRegisterExactEightCharsAccepted() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn("User registered successfully");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"12345678\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Registration: Password exactly 128 characters accepted")
    void testRegisterExact128CharsAccepted() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn("User registered successfully");
        String pass128 = "A".repeat(128);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"%s\"}", pass128)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Registration: Password shorter than 8 characters rejected (400 Bad Request)")
    void testRegisterShortPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"short7!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Password must be between 8 and 128 characters")));

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("Registration: Password longer than 128 characters rejected (400 Bad Request)")
    void testRegisterLongPasswordRejected() throws Exception {
        String pass129 = "A".repeat(129);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"%s\"}", pass129)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Password must be between 8 and 128 characters")));

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("Registration: Empty or blank password rejected (400 Bad Request)")
    void testRegisterBlankPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("Registration: Password with leading/trailing whitespace rejected (400 Bad Request)")
    void testRegisterWhitespacePasswordRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\" ValidPass123 \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("leading or trailing whitespace")));

        verify(authService, never()).register(any());
    }

    // ─── Reset Password Endpoint Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Password Reset: Valid password accepted")
    void testResetPasswordValidAccepted() throws Exception {
        when(authService.verifyOtpAndResetPassword("alex@example.com", "123456", "NewStrongPass123"))
                .thenReturn("Password updated successfully.");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"otp\":\"123456\",\"newPassword\":\"NewStrongPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully."));
    }

    @Test
    @DisplayName("Password Reset: Short password rejected (400 Bad Request)")
    void testResetPasswordShortRejected() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"otp\":\"123456\",\"newPassword\":\"pass7\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Password must be between 8 and 128 characters")));

        verify(authService, never()).verifyOtpAndResetPassword(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Password Reset: Password with leading/trailing whitespace rejected (400 Bad Request)")
    void testResetPasswordWhitespaceRejected() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"otp\":\"123456\",\"newPassword\":\" pass12345 \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("leading or trailing whitespace")));

        verify(authService, never()).verifyOtpAndResetPassword(anyString(), anyString(), anyString());
    }

    // ─── AuthService Defense-in-Depth Tests ─────────────────────────────────────

    @Test
    @DisplayName("AuthService: register rejects invalid password before hashing or saving")
    void testAuthServiceRegisterValidatesPassword() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtUtil, authenticationManager, mailSender, otpRateLimiter);

        RegisterRequest weakRequest = RegisterRequest.builder()
                .name("Alex")
                .email("alex@example.com")
                .password("weak")
                .build();

        assertThatThrownBy(() -> service.register(weakRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must be between 8 and 128 characters.");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("AuthService: verifyOtpAndResetPassword rejects invalid password before hashing or saving")
    void testAuthServiceResetPasswordValidatesPassword() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtUtil, authenticationManager, mailSender, otpRateLimiter);

        String email = "alex@example.com";
        String validOtp = "654321";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .resetToken(validOtp)
                .resetTokenExpiry(LocalDateTime.now().plusMinutes(5))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyOtpAndResetPassword(email, validOtp, "short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must be between 8 and 128 characters.");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
