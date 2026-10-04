package com.Wealthify.backend.service;

import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.security.JwtUtil;
import com.Wealthify.backend.security.OtpRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceOtpTest {

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

    @Spy
    private OtpRateLimiter otpRateLimiter = new OtpRateLimiter(3, 3, 600, 5, 900);

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        otpRateLimiter.resetAll();
        ReflectionTestUtils.setField(authService, "senderEmail", "noreply@wealthify.test");
    }

    @Test
    @DisplayName("Generated OTPs strictly conform to 6-digit numeric zero-padded format [000000-999999]")
    void testOtpFormatAndLength() {
        for (int i = 0; i < 1000; i++) {
            String otp = authService.generateOtp();

            assertThat(otp).isNotNull();
            assertThat(otp).hasSize(6);
            assertThat(otp).matches("^[0-9]{6}$");

            int intVal = Integer.parseInt(otp);
            assertThat(intVal).isBetween(0, 999999);
        }
    }

    @Test
    @DisplayName("Generated OTPs demonstrate high entropy and are not sequential or deterministic")
    void testOtpEntropyAndNonPredictability() {
        Set<String> distinctOtps = new HashSet<>();
        List<Integer> numericOtps = new ArrayList<>();

        int sampleSize = 100;
        for (int i = 0; i < sampleSize; i++) {
            String otp = authService.generateOtp();
            distinctOtps.add(otp);
            numericOtps.add(Integer.parseInt(otp));
        }

        // With a 1,000,000 range and 100 samples, collisions should be very rare (>90 unique values)
        assertThat(distinctOtps.size()).isGreaterThan(90);

        // Verify values are not sequential increments (e.g. n, n+1, n+2)
        boolean hasNonSequential = false;
        for (int i = 1; i < numericOtps.size(); i++) {
            if (numericOtps.get(i) - numericOtps.get(i - 1) != 1) {
                hasNonSequential = true;
                break;
            }
        }
        assertThat(hasNonSequential).isTrue();
    }

    @Test
    @DisplayName("processForgotPassword generates secure OTP, assigns 5-minute expiry, and dispatches email")
    void testProcessForgotPasswordGeneratesSecureOtp() {
        String email = "student@example.com";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Alex Kumar")
                .password("hashed_pw")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        authService.processForgotPassword(email);

        // Capture saved user
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getResetToken()).isNotNull();
        assertThat(savedUser.getResetToken()).matches("^[0-9]{6}$");
        assertThat(savedUser.getResetTokenExpiry()).isAfter(LocalDateTime.now().plusMinutes(4));
        assertThat(savedUser.getResetTokenExpiry()).isBefore(LocalDateTime.now().plusMinutes(6));

        // Capture dispatched email
        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(mailCaptor.capture());
        SimpleMailMessage sentMessage = mailCaptor.getValue();

        assertThat(sentMessage.getTo()).containsExactly(email);
        assertThat(sentMessage.getFrom()).isEqualTo("noreply@wealthify.test");
        assertThat(sentMessage.getSubject()).isEqualTo("Wealthify - Password Reset OTP");
        assertThat(sentMessage.getText()).contains(savedUser.getResetToken());
        assertThat(sentMessage.getText()).contains("valid for 5 minutes");
    }

    @Test
    @DisplayName("Consecutive processForgotPassword calls produce different cryptographically secure OTPs")
    void testMultipleForgotPasswordCallsGenerateUniqueOtps() {
        String email = "student@example.com";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Alex Kumar")
                .password("hashed_pw")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        authService.processForgotPassword(email);
        String firstOtp = user.getResetToken();

        authService.processForgotPassword(email);
        String secondOtp = user.getResetToken();

        assertThat(firstOtp).matches("^[0-9]{6}$");
        assertThat(secondOtp).matches("^[0-9]{6}$");
        assertThat(firstOtp).isNotEqualTo(secondOtp);
    }

    @Test
    @DisplayName("verifyOtpAndResetPassword successfully resets password and clears reset token/expiry")
    void testVerifyOtpSuccessAndClearing() {
        String email = "student@example.com";
        String validOtp = "654321";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Alex Kumar")
                .password("old_hash")
                .resetToken(validOtp)
                .resetTokenExpiry(LocalDateTime.now().plusMinutes(3))
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newSecurePassword123")).thenReturn("new_hash_123");

        String result = authService.verifyOtpAndResetPassword(email, validOtp, "newSecurePassword123");

        assertThat(result).isEqualTo("Password updated successfully.");
        assertThat(user.getPassword()).isEqualTo("new_hash_123");
        assertThat(user.getResetToken()).isNull();
        assertThat(user.getResetTokenExpiry()).isNull();
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("verifyOtpAndResetPassword rejects invalid OTP, expired OTP, and nonexistent email uniformly")
    void testVerifyOtpRejectionOnInvalidOrExpired() {
        String email = "student@example.com";

        // Test 1: Invalid OTP
        User userWithValidOtp = User.builder()
                .email(email)
                .resetToken("123456")
                .resetTokenExpiry(LocalDateTime.now().plusMinutes(3))
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userWithValidOtp));

        assertThatThrownBy(() -> authService.verifyOtpAndResetPassword(email, "999999", "newPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired verification code.");

        // Test 2: Expired OTP
        User userWithExpiredOtp = User.builder()
                .email(email)
                .resetToken("123456")
                .resetTokenExpiry(LocalDateTime.now().minusMinutes(1))
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userWithExpiredOtp));

        assertThatThrownBy(() -> authService.verifyOtpAndResetPassword(email, "123456", "newPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired verification code.");

        // Test 3: Nonexistent account (SEC-07: anti-enumeration)
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyOtpAndResetPassword("nonexistent@example.com", "123456", "newPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired verification code.");
    }

    @Test
    @DisplayName("processForgotPassword handles email dispatch failure gracefully without leaking account existence")
    void testProcessForgotPasswordEmailFailureHandledGracefully() {
        String email = "student@example.com";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .name("Alex Kumar")
                .password("hashed_pw")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        doThrow(new org.springframework.mail.MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Must not throw an exception to caller
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> authService.processForgotPassword(email));

        // OTP should have been cleared on dispatch failure to avoid dangling token
        assertThat(user.getResetToken()).isNull();
        assertThat(user.getResetTokenExpiry()).isNull();
    }
}
