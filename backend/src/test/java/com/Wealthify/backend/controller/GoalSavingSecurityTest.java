package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.GoalResponse;
import com.Wealthify.backend.entity.Goal;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.exception.GlobalExceptionHandler;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.GoalRepository;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.service.AiService;
import com.Wealthify.backend.service.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VAL-01 Regression Test Suite: Goal Saving Validation & Defense Against Negative Decrements
 *
 * Verifies that:
 * 1. Negative amounts are strictly rejected by both controller and service layers.
 * 2. Zero amounts are rejected consistently with Wealthify's financial validation rules.
 * 3. Missing amount parameters return an appropriate HTTP 400 client error.
 * 4. Legitimate positive amounts increase currentSaved correctly.
 * 5. Very large positive amounts are accepted and can trigger "ACHIEVED" status.
 * 6. Non-numeric amounts fail cleanly.
 * 7. Goal ownership verification remains enforced.
 * 8. Domain state invariant: currentSaved is never reduced below its prior value.
 */
@ExtendWith(MockitoExtension.class)
class GoalSavingSecurityTest {

    private MockMvc mockMvc;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private AiService aiService;

    private GoalService goalService;
    private GoalController goalController;

    private static final String USER_EMAIL = "saver@example.com";
    private static final String OTHER_EMAIL = "intruder@example.com";
    private static final UUID GOAL_ID = UUID.randomUUID();
    private static final BigDecimal INITIAL_SAVED = BigDecimal.valueOf(5000.00);
    private static final BigDecimal TARGET_AMOUNT = BigDecimal.valueOf(20000.00);

    private User owner;
    private Goal existingGoal;

    @BeforeEach
    void setUp() {
        goalService = new GoalService(goalRepository, userRepository, expenseRepository, aiService);
        goalController = new GoalController(goalService);

        // HandlerMethodArgumentResolver to inject the authenticated user into @AuthenticationPrincipal
        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                UserDetails userDetails = mock(UserDetails.class);
                lenient().when(userDetails.getUsername()).thenReturn(USER_EMAIL);
                return userDetails;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(goalController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        owner = User.builder()
                .id(UUID.randomUUID())
                .email(USER_EMAIL)
                .name("Goal Saver")
                .monthlyIncome(BigDecimal.valueOf(50000.00))
                .savingPercentage(BigDecimal.valueOf(20.0))
                .build();

        existingGoal = Goal.builder()
                .id(GOAL_ID)
                .user(owner)
                .itemName("Laptop")
                .targetAmount(TARGET_AMOUNT)
                .targetDate(LocalDate.now().plusMonths(6))
                .currentSaved(INITIAL_SAVED)
                .status("ACTIVE")
                .build();
    }

    // ─── Controller Boundary Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("VAL-01 Test 1: Negative saving amount is rejected with HTTP 400 Bad Request and does not mutate goal")
    void testNegativeAmountRejectedByController() throws Exception {
        mockMvc.perform(patch("/api/goals/" + GOAL_ID + "/save")
                        .param("amount", "-1000.00")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Saving amount must be positive."));

        // Domain invariant: goal was never saved, currentSaved remains unchanged
        verify(goalRepository, never()).save(any(Goal.class));
        assertThat(existingGoal.getCurrentSaved()).isEqualByComparingTo(INITIAL_SAVED);
    }

    @Test
    @DisplayName("VAL-01 Test 2: Zero saving amount is rejected with HTTP 400 Bad Request")
    void testZeroAmountRejectedByController() throws Exception {
        mockMvc.perform(patch("/api/goals/" + GOAL_ID + "/save")
                        .param("amount", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Saving amount must be positive."));

        verify(goalRepository, never()).save(any(Goal.class));
        assertThat(existingGoal.getCurrentSaved()).isEqualByComparingTo(INITIAL_SAVED);
    }

    @Test
    @DisplayName("VAL-01 Test 3: Valid positive saving amount succeeds and correctly increases currentSaved")
    void testPositiveAmountSucceeds() throws Exception {
        when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(existingGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/goals/" + GOAL_ID + "/save")
                        .param("amount", "2500.00")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSaved").value(7500.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify goal state mutation in database
        verify(goalRepository, times(1)).save(existingGoal);
        assertThat(existingGoal.getCurrentSaved()).isEqualByComparingTo(BigDecimal.valueOf(7500.00));
    }

    @Test
    @DisplayName("VAL-01 Test 4: Missing amount query parameter returns HTTP 400 client error without mutating state")
    void testMissingAmountReturnsClientError() throws Exception {
        mockMvc.perform(patch("/api/goals/" + GOAL_ID + "/save")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Saving amount must be positive."));

        verify(goalRepository, never()).save(any(Goal.class));
        assertThat(existingGoal.getCurrentSaved()).isEqualByComparingTo(INITIAL_SAVED);
    }

    @Test
    @DisplayName("VAL-01 Test 5: Very large positive saving amount is accepted and transitions status to ACHIEVED")
    void testVeryLargePositiveAmountTransitionsToAchieved() throws Exception {
        when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(existingGoal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Saving 50,000 when target is 20,000 (currently 5,000 saved) -> total 55,000
        mockMvc.perform(patch("/api/goals/" + GOAL_ID + "/save")
                        .param("amount", "50000.00")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSaved").value(55000.0))
                .andExpect(jsonPath("$.status").value("ACHIEVED"));

        assertThat(existingGoal.getStatus()).isEqualTo("ACHIEVED");
        assertThat(existingGoal.getCurrentSaved()).isEqualByComparingTo(BigDecimal.valueOf(55000.00));
    }

    @Test
    @DisplayName("VAL-01: Non-numeric amount parameter is rejected with HTTP 400 Bad Request")
    void testNonNumericAmountRejected() throws Exception {
        mockMvc.perform(patch("/api/goals/" + GOAL_ID + "/save")
                        .param("amount", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    @DisplayName("VAL-01 Test 6: Goal ownership verification is preserved and rejects unauthorized access")
    void testOwnershipVerificationPreserved() throws Exception {
        // Goal belongs to OTHER_EMAIL, but authenticated principal is USER_EMAIL
        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .email(OTHER_EMAIL)
                .name("Other User")
                .build();
        Goal otherGoal = Goal.builder()
                .id(GOAL_ID)
                .user(otherUser)
                .itemName("Car")
                .targetAmount(TARGET_AMOUNT)
                .targetDate(LocalDate.now().plusMonths(12))
                .currentSaved(INITIAL_SAVED)
                .status("ACTIVE")
                .build();

        when(goalRepository.findById(GOAL_ID)).thenReturn(Optional.of(otherGoal));

        mockMvc.perform(patch("/api/goals/" + GOAL_ID + "/save")
                        .param("amount", "1000.00")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        verify(goalRepository, never()).save(otherGoal);
        assertThat(otherGoal.getCurrentSaved()).isEqualByComparingTo(INITIAL_SAVED);
    }

    // ─── Service Layer Defense in Depth Tests ──────────────────────────────────

    @Test
    @DisplayName("VAL-01 Service Guard: updateGoalSaving rejects negative amount directly")
    void testServiceRejectsNegativeAmount() {
        assertThatThrownBy(() -> goalService.updateGoalSaving(USER_EMAIL, GOAL_ID, BigDecimal.valueOf(-500.00)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Saving amount must be positive.");

        verify(goalRepository, never()).findById(any());
        verify(goalRepository, never()).save(any());
    }

    @Test
    @DisplayName("VAL-01 Service Guard: updateGoalSaving rejects zero amount directly")
    void testServiceRejectsZeroAmount() {
        assertThatThrownBy(() -> goalService.updateGoalSaving(USER_EMAIL, GOAL_ID, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Saving amount must be positive.");

        verify(goalRepository, never()).findById(any());
        verify(goalRepository, never()).save(any());
    }

    @Test
    @DisplayName("VAL-01 Service Guard: updateGoalSaving rejects null amount directly")
    void testServiceRejectsNullAmount() {
        assertThatThrownBy(() -> goalService.updateGoalSaving(USER_EMAIL, GOAL_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Saving amount must be positive.");

        verify(goalRepository, never()).findById(any());
        verify(goalRepository, never()).save(any());
    }

    @Test
    @DisplayName("VAL-01 Domain Invariant: Goal currentSaved can never decrease after rejected attempts")
    void testDomainInvariantCurrentSavedNeverDecreases() {
        BigDecimal[] invalidAmounts = {
                BigDecimal.valueOf(-10000.00),
                BigDecimal.valueOf(-0.01),
                BigDecimal.ZERO,
                null
        };

        for (BigDecimal invalid : invalidAmounts) {
            assertThatThrownBy(() -> goalService.updateGoalSaving(USER_EMAIL, GOAL_ID, invalid))
                    .isInstanceOf(IllegalArgumentException.class);
            // Verify invariant: currentSaved has not decreased
            assertThat(existingGoal.getCurrentSaved()).isGreaterThanOrEqualTo(INITIAL_SAVED);
        }
    }
}
