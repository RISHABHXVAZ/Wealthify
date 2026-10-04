package com.Wealthify.backend.exception;

import com.Wealthify.backend.controller.AuthController;
import com.Wealthify.backend.controller.GoalController;
import com.Wealthify.backend.dto.ResetPasswordRequest;
import com.Wealthify.backend.security.OtpRateLimiter;
import com.Wealthify.backend.service.AuthService;
import com.Wealthify.backend.service.GoalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerSecurityTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private OtpRateLimiter otpRateLimiter;

    @Mock
    private GoalService goalService;

    @Mock
    private UserDetails userDetails;

    // Dummy test controller for simulating varied exception conditions
    @RestController
    @RequestMapping("/test/security")
    static class TestExceptionController {

        @GetMapping("/sql-error")
        public void throwSqlError() {
            throw new RuntimeException("SQL grammar error: users.secret_column");
        }

        @GetMapping("/sensitive-db-info")
        public void throwSensitiveDbInfo() {
            throw new RuntimeException("SQLSTATE=23505; jdbc:postgresql://internal-db:5432/wealthify");
        }

        @GetMapping("/npe-error")
        public void throwNullPointer() {
            throw new NullPointerException("Cannot invoke 'com.Wealthify.backend.entity.User.getId()' because 'user' is null");
        }

        @GetMapping("/generic-checked-exception")
        public void throwCheckedException() throws Exception {
            throw new Exception("Unexpected filesystem IO failure at /etc/wealthify/secrets/config.key");
        }

        @GetMapping("/business-unauthorized")
        public void throwBusinessUnauthorized() {
            throw new BusinessException("Unauthorized");
        }

        @GetMapping("/business-invalid-code")
        public void throwBusinessInvalidCode() {
            throw new BusinessException("Invalid or expired verification code.");
        }

        @GetMapping("/illegal-argument")
        public void throwIllegalArgument() {
            throw new IllegalArgumentException("Saving amount must be positive.");
        }

        @GetMapping("/rate-limit")
        public void throwRateLimit() {
            throw new RateLimitExceededException("Too many password reset requests. Please try again in 180 seconds.", 180);
        }

        @GetMapping("/bad-credentials")
        public void throwBadCredentials() {
            throw new BadCredentialsException("Internal credential comparison failed for hash $2a$10$xyz");
        }

        @PostMapping("/validation")
        public String validateInput(@Valid @RequestBody SampleValidationDto dto) {
            return "valid";
        }
    }

    @Data
    static class SampleValidationDto {
        @NotBlank(message = "Username must not be blank")
        private String username;
    }

    @BeforeEach
    void setUp() {
        TestExceptionController testController = new TestExceptionController();
        AuthController authController = new AuthController(authService, otpRateLimiter);
        GoalController goalController = new GoalController(goalService);

        org.springframework.web.method.support.HandlerMethodArgumentResolver authPrincipalResolver =
                new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                                  org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                                  org.springframework.web.context.request.NativeWebRequest webRequest,
                                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return userDetails;
                    }
                };

        mockMvc = MockMvcBuilders.standaloneSetup(testController, authController, goalController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Test 1: Unexpected RuntimeException ─────────────────────────────────

    @Test
    @DisplayName("Test 1: Unexpected RuntimeException returns HTTP 500 and sanitized generic message")
    void test1_unexpectedRuntimeException_returns500AndSanitizedGenericMessage() throws Exception {
        mockMvc.perform(get("/test/security/sql-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.GENERIC_ERROR_MESSAGE))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("SQL");
                    assertThat(body).doesNotContain("secret_column");
                    assertThat(body).doesNotContain("grammar");
                    assertThat(body).doesNotContain("users");
                });
    }

    // ─── Test 2: Expected Business Exception ─────────────────────────────────

    @Test
    @DisplayName("Test 2: Expected business exception preserves client message and HTTP 400")
    void test2_expectedBusinessException_preservesStatusAndClientMessage() throws Exception {
        mockMvc.perform(get("/test/security/business-unauthorized"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        mockMvc.perform(get("/test/security/business-invalid-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."));
    }

    // ─── Test 3: Existing Validation Behavior ────────────────────────────────

    @Test
    @DisplayName("Test 3: Bean Validation errors return HTTP 400 with field validation error message")
    void test3_beanValidationException_returns400AndFieldMessage() throws Exception {
        mockMvc.perform(post("/test/security/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("username: Username must not be blank"));
    }

    // ─── Test 4: SEC-07 Compatibility ────────────────────────────────────────

    @Test
    @DisplayName("Test 4: SEC-07 password reset enumeration protection remains intact and uniform")
    void test4_sec07_passwordResetEnumerationProtectionRemainsIntact() throws Exception {
        when(authService.verifyOtpAndResetPassword(eq("nonexistent@example.com"), eq("123456"), any()))
                .thenThrow(new BusinessException("Invalid or expired verification code."));
        when(authService.verifyOtpAndResetPassword(eq("existing@example.com"), eq("999999"), any()))
                .thenThrow(new BusinessException("Invalid or expired verification code."));

        MvcResult nonexistentResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@example.com\",\"otp\":\"123456\",\"newPassword\":\"NewValidP@ssw0rd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."))
                .andReturn();

        MvcResult invalidOtpResult = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"existing@example.com\",\"otp\":\"999999\",\"newPassword\":\"NewValidP@ssw0rd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired verification code."))
                .andReturn();

        assertThat(nonexistentResult.getResponse().getContentAsString())
                .isEqualTo(invalidOtpResult.getResponse().getContentAsString());
    }

    // ─── Test 5: VAL-01 Compatibility ────────────────────────────────────────

    @Test
    @DisplayName("Test 5: VAL-01 negative/zero goal savings return HTTP 400 and validation message, not 500")
    void test5_val01_negativeGoalSavingReturns400() throws Exception {
        UUID goalId = UUID.randomUUID();

        // Negative amount
        mockMvc.perform(patch("/api/goals/" + goalId + "/save")
                        .param("amount", "-1000.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Saving amount must be positive."));

        // Zero amount
        mockMvc.perform(patch("/api/goals/" + goalId + "/save")
                        .param("amount", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Saving amount must be positive."));
    }

    // ─── Test 6: No Sensitive Information Leakage ───────────────────────────

    @Test
    @DisplayName("Test 6: Unexpected exception with SQLSTATE and JDBC details does not expose details to client")
    void test6_sensitiveInformationLeakagePrevented() throws Exception {
        mockMvc.perform(get("/test/security/sensitive-db-info"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.GENERIC_ERROR_MESSAGE))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("23505");
                    assertThat(body).doesNotContain("jdbc");
                    assertThat(body).doesNotContain("postgresql");
                    assertThat(body).doesNotContain("internal-db");
                    assertThat(body).doesNotContain("5432");
                    assertThat(body).doesNotContain("wealthify");
                });
    }

    // ─── Defense-in-depth: Additional Handlers ────────────────────────────────

    @Test
    @DisplayName("Test 7: NullPointerException and checked Exceptions return HTTP 500 without disclosing internals")
    void test7_nullPointerAndCheckedExceptions_return500Sanitized() throws Exception {
        mockMvc.perform(get("/test/security/npe-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.GENERIC_ERROR_MESSAGE))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("NullPointerException");
                    assertThat(body).doesNotContain("getId");
                    assertThat(body).doesNotContain("User");
                });

        mockMvc.perform(get("/test/security/generic-checked-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.GENERIC_ERROR_MESSAGE))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("filesystem");
                    assertThat(body).doesNotContain("/etc/wealthify");
                    assertThat(body).doesNotContain("config.key");
                });
    }

    @Test
    @DisplayName("Test 8: RateLimitExceededException returns HTTP 429 with Retry-After header")
    void test8_rateLimitExceeded_returns429WithHeader() throws Exception {
        mockMvc.perform(get("/test/security/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "180"))
                .andExpect(jsonPath("$.message").value("Too many password reset requests. Please try again in 180 seconds."));
    }

    @Test
    @DisplayName("Test 9: BadCredentialsException returns HTTP 401 with sanitized message")
    void test9_badCredentials_returns401WithSanitizedMessage() throws Exception {
        mockMvc.perform(get("/test/security/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("LDAP");
                    assertThat(body).doesNotContain("hash");
                    assertThat(body).doesNotContain("$2a$10$");
                });
    }
}
