package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RestTemplate restTemplate = new RestTemplate(); // Built-in HTTP client

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${resend.api.key}")
    private String resendApiKey;

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
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("Forgot password requested for non-existent email: {}", email);
            return;
        }

        String token = java.util.UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        // Construct the HTTP call payload for Resend API over standard Port 443
        String url = "https://api.resend.com/emails";

        Map<String, Object> requestBody = Map.of(
                "from", "onboarding@resend.dev", // Resend provides this default verified address for free accounts
                "to", user.getEmail(),
                "subject", "Wealthify - Reset Your Password",
                "text", "Click the link below to securely reset your password. It is valid for 15 minutes:\n\n" + resetUrl
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            log.info("Resend API response status: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to send transactional email via Resend API: {}", e.getMessage());
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