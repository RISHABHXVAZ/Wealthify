package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public String register(RegisterRequest request) {
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
        // Protects against user enumeration attacks
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        String token = java.util.UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(user.getEmail());
            message.setSubject("Wealthify - Reset Your Password");
            message.setText("Click the link below to securely reset your password. It is valid for 15 minutes:\n\n" + resetUrl);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to dispatch password email.");
        }
    }

    public String updatePasswordWithToken(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired password reset token."));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired.");
        }

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