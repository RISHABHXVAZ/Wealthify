package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.Wealthify.backend.security.OtpRateLimiter;
import com.Wealthify.backend.security.PasswordValidator;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final JavaMailSender mailSender; // Injects the Gmail SMTP configurations cleanly
    private final OtpRateLimiter otpRateLimiter;

    @Value("${spring.mail.username}")
    private String senderEmail;

    String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    public String register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request cannot be null");
        }
        PasswordValidator.validate(request.getPassword());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);
        return "User registered successfully";
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        String token = jwtUtil.generateToken(user.getEmail());
        return new LoginResponse(token, user.getName(), user.getEmail());
    }

    public void processForgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("Forgot password requested for non-existent email: {}", email);
            return;
        }

        // Generate cryptographically secure 6-digit OTP
        String otp = generateOtp();

        user.setResetToken(otp);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(user.getEmail()); // Sends to any user safely
            message.setSubject("Wealthify - Password Reset OTP");
            message.setText("Your One-Time Password (OTP) for resetting your Wealthify password is: " + otp + "\n\nThis OTP is secure and valid for 5 minutes.");

            mailSender.send(message);
            log.info("OTP verification email dispatched cleanly via Gmail SMTP server.");
        } catch (Exception e) {
            log.error("Failed to dispatch password verification email: {}", e.getMessage());
            // Prevent account enumeration: do not expose email dispatch errors to external callers.
            // Clear un-dispatched reset token so an undelivered OTP cannot be guessed or reused.
            try {
                user.setResetToken(null);
                user.setResetTokenExpiry(null);
                userRepository.save(user);
            } catch (Exception rollbackEx) {
                log.error("Failed to rollback reset token after dispatch failure: {}", rollbackEx.getMessage());
            }
        }
    }

    public String verifyOtpAndResetPassword(String email, String otp, String newPassword) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required.");
        }

        otpRateLimiter.checkVerificationAllowed(email);

        User user = userRepository.findByEmail(email).orElse(null);
        boolean isInvalid = (user == null)
                || (user.getResetToken() == null)
                || (!user.getResetToken().equals(otp))
                || (user.getResetTokenExpiry() == null)
                || (user.getResetTokenExpiry().isBefore(LocalDateTime.now()));

        if (isInvalid) {
            otpRateLimiter.recordVerificationFailure(email);
            throw new RuntimeException("Invalid or expired verification code.");
        }

        PasswordValidator.validate(newPassword);

        otpRateLimiter.recordVerificationSuccess(email);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        return "Password updated successfully.";
    }

    public String updateIncome(String email, BigDecimal income) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setMonthlyIncome(income);
        userRepository.save(user);
        return "Monthly income updated to ₹" + income;
    }
}