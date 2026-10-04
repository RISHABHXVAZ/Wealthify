package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.*;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.Goal;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.exception.GlobalExceptionHandler;
import com.Wealthify.backend.repository.CategoryRepository;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.GoalRepository;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.service.AiService;
import com.Wealthify.backend.service.ExpenseService;
import com.Wealthify.backend.service.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI-03 Regression Test Suite:
 * Verifies that user-controlled AI prompt inputs (ExpenseRequest.description and GoalRequest.itemName)
 * are strictly bounded at the backend request boundary, preventing token amplification,
 * prompt structure abuse, and context-window exhaustion.
 */
@ExtendWith(MockitoExtension.class)
class AiPromptInputBoundingSecurityTest {

    private static final String USER_EMAIL = "student@wealthify.com";

    private MockMvc expenseMockMvc;
    private MockMvc goalMockMvc;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private GoalService goalService;

    @Mock
    private AiService aiService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GoalRepository goalRepository;

    private ObjectMapper objectMapper;
    private User testUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

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

        ExpenseController expenseController = new ExpenseController(expenseService);
        expenseMockMvc = MockMvcBuilders.standaloneSetup(expenseController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        GoalController goalController = new GoalController(goalService);
        goalMockMvc = MockMvcBuilders.standaloneSetup(goalController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email(USER_EMAIL)
                .name("Test User")
                .monthlyIncome(BigDecimal.valueOf(50000))
                .savingPercentage(BigDecimal.valueOf(20))
                .build();
    }

    @Test
    @DisplayName("AI-03 Test 1: Expense description exactly at maximum length (150 chars) is accepted")
    void testExpenseDescriptionAtMaxLengthAccepted() throws Exception {
        String exact150Chars = "A".repeat(150);

        ExpenseRequest request = new ExpenseRequest();
        request.setAmount(BigDecimal.valueOf(250.00));
        request.setDescription(exact150Chars);

        ExpenseResponse mockResponse = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(250.00))
                .description(exact150Chars)
                .build();

        when(expenseService.addExpense(eq(USER_EMAIL), any(ExpenseRequest.class)))
                .thenReturn(mockResponse);

        expenseMockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(exact150Chars));

        verify(expenseService, times(1)).addExpense(eq(USER_EMAIL), any(ExpenseRequest.class));
    }

    @Test
    @DisplayName("AI-03 Test 2: Expense description exceeding maximum length (151 chars) is rejected with 400")
    void testExpenseDescriptionAboveMaxLengthRejected() throws Exception {
        String overLimit151Chars = "A".repeat(151);

        ExpenseRequest request = new ExpenseRequest();
        request.setAmount(BigDecimal.valueOf(250.00));
        request.setDescription(overLimit151Chars);

        expenseMockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description: Description must not exceed 150 characters"));

        verify(expenseService, never()).addExpense(anyString(), any(ExpenseRequest.class));
    }

    @Test
    @DisplayName("AI-03 Test 3: Goal item name exactly at maximum length (100 chars) is accepted")
    void testGoalItemNameAtMaxLengthAccepted() throws Exception {
        String exact100Chars = "B".repeat(100);

        GoalRequest request = new GoalRequest();
        request.setItemName(exact100Chars);
        request.setTargetAmount(BigDecimal.valueOf(50000.00));
        request.setTargetDate(LocalDate.now().plusMonths(6));

        GoalResponse mockResponse = GoalResponse.builder()
                .id(UUID.randomUUID())
                .itemName(exact100Chars)
                .targetAmount(BigDecimal.valueOf(50000.00))
                .build();

        when(goalService.createGoal(eq(USER_EMAIL), any(GoalRequest.class)))
                .thenReturn(mockResponse);

        goalMockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value(exact100Chars));

        verify(goalService, times(1)).createGoal(eq(USER_EMAIL), any(GoalRequest.class));
    }

    @Test
    @DisplayName("AI-03 Test 4: Goal item name exceeding maximum length (101 chars) is rejected with 400")
    void testGoalItemNameAboveMaxLengthRejected() throws Exception {
        String overLimit101Chars = "B".repeat(101);

        GoalRequest request = new GoalRequest();
        request.setItemName(overLimit101Chars);
        request.setTargetAmount(BigDecimal.valueOf(50000.00));
        request.setTargetDate(LocalDate.now().plusMonths(6));

        goalMockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("itemName: Item name must not exceed 100 characters"));

        verify(goalService, never()).createGoal(anyString(), any(GoalRequest.class));
    }

    @Test
    @DisplayName("AI-03 Test 5: Existing valid expense creation flow works and AI categorization is unaffected")
    void testExistingValidExpenseFlow() {
        ExpenseService realExpenseService = new ExpenseService(
                expenseRepository, userRepository, categoryRepository, aiService
        );

        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AiCategorizationResult aiResult = new AiCategorizationResult();
        aiResult.setCategory("Food");
        aiResult.setType("NEED");
        aiResult.setWasteful(false);
        aiResult.setConfidence(0.95);
        aiResult.setReason("Legitimate food expense");
        when(aiService.categorizeExpense(eq("College Canteen Lunch"), any(), any(), any()))
                .thenReturn(aiResult);

        Category foodCategory = Category.builder().id(1).name("Food").type("NEED").isEssential(true).build();
        when(categoryRepository.findByNameIgnoreCase("Food")).thenReturn(Optional.of(foodCategory));

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        ExpenseRequest request = new ExpenseRequest();
        request.setAmount(BigDecimal.valueOf(150.00));
        request.setDescription("College Canteen Lunch");

        ExpenseResponse response = realExpenseService.addExpense(USER_EMAIL, request);

        assertThat(response).isNotNull();
        assertThat(response.getDescription()).isEqualTo("College Canteen Lunch");
        assertThat(response.getCategory().getName()).isEqualTo("Food");
        verify(aiService, times(1)).categorizeExpense(eq("College Canteen Lunch"), any(), any(), any());
        verify(aiService, times(1)).evictUserCache(testUser.getId());
    }

    @Test
    @DisplayName("AI-03 Test 6: Existing valid goal creation flow works")
    void testExistingValidGoalFlow() {
        GoalService realGoalService = new GoalService(
                goalRepository, userRepository, expenseRepository, aiService
        );

        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(aiService.generateGoalPlan(any(), eq("MacBook Air"), any(), any(), any(), any(), any(), any()))
                .thenReturn("Save ₹10,000 per month for 8 months.");

        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            g.setId(UUID.randomUUID());
            if (g.getCurrentSaved() == null) {
                g.setCurrentSaved(BigDecimal.ZERO);
            }
            return g;
        });

        GoalRequest request = new GoalRequest();
        request.setItemName("MacBook Air");
        request.setTargetAmount(BigDecimal.valueOf(80000.00));
        request.setTargetDate(LocalDate.now().plusMonths(8));

        GoalResponse response = realGoalService.createGoal(USER_EMAIL, request);

        assertThat(response).isNotNull();
        assertThat(response.getItemName()).isEqualTo("MacBook Air");
        assertThat(response.getAiPlan()).isEqualTo("Save ₹10,000 per month for 8 months.");
        verify(aiService, times(1)).generateGoalPlan(eq(testUser.getId()), eq("MacBook Air"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AI-03 Test 7: Direct API call with massive payload cannot bypass backend validation")
    void testDirectApiMassivePayloadCannotBypass() throws Exception {
        String massive5000Chars = "C".repeat(5000);

        ExpenseRequest request = new ExpenseRequest();
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setDescription(massive5000Chars);

        expenseMockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description: Description must not exceed 150 characters"));

        verify(expenseService, never()).addExpense(anyString(), any(ExpenseRequest.class));
    }

    @Test
    @DisplayName("AI-03 Test 8: AiService prompt input bounded — inputs reaching AiService are bounded in length")
    void testInputReachingAiServiceIsBounded() {
        ExpenseService realExpenseService = new ExpenseService(
                expenseRepository, userRepository, categoryRepository, aiService
        );

        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AiCategorizationResult aiResult = new AiCategorizationResult();
        aiResult.setCategory("Miscellaneous");
        aiResult.setType("WANT");
        aiResult.setWasteful(false);
        aiResult.setConfidence(0.9);

        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        when(aiService.categorizeExpense(descCaptor.capture(), any(), any(), any()))
                .thenReturn(aiResult);

        when(categoryRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        String maxLengthDesc = "X".repeat(150);
        ExpenseRequest request = new ExpenseRequest();
        request.setAmount(BigDecimal.valueOf(200.00));
        request.setDescription(maxLengthDesc);

        realExpenseService.addExpense(USER_EMAIL, request);

        assertThat(descCaptor.getValue()).isNotNull();
        assertThat(descCaptor.getValue().length()).isLessThanOrEqualTo(150);
    }
}
