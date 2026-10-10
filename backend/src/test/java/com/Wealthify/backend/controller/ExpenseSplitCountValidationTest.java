package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.AiCategorizationResult;
import com.Wealthify.backend.dto.CategoryDto;
import com.Wealthify.backend.dto.ExpenseRequest;
import com.Wealthify.backend.dto.ExpenseResponse;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.exception.GlobalExceptionHandler;
import com.Wealthify.backend.repository.CategoryRepository;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.UserRepository;
import com.Wealthify.backend.service.AiService;
import com.Wealthify.backend.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExpenseSplitCountValidationTest {

    private static final String TEST_USER = "user@example.com";

    @Mock
    private ExpenseService expenseService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AiService aiService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ExpenseController expenseController = new ExpenseController(expenseService);

        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(TEST_USER, "password", Collections.emptyList());

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
                return principal;
            }
        };

        mockMvc = MockMvcBuilders
                .standaloneSetup(expenseController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================================
    // 1. Controller / Request-Validation Boundary Tests
    // =========================================================================

    @Test
    @DisplayName("VAL-03 Test 1: Omitted splitCount is valid and accepted (optional behavior preserved)")
    void testOmittedSplitCountAccepted() throws Exception {
        ExpenseResponse response = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100.00))
                .description("Team lunch")
                .build();

        when(expenseService.addExpense(eq(TEST_USER), any(ExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Team lunch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.description").value("Team lunch"));

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).addExpense(eq(TEST_USER), captor.capture());
        assertThat(captor.getValue().getSplitCount()).isNull();
    }

    @Test
    @DisplayName("VAL-03 Test 2: Explicit null splitCount is valid and accepted")
    void testExplicitNullSplitCountAccepted() throws Exception {
        ExpenseResponse response = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100.00))
                .description("Team lunch")
                .build();

        when(expenseService.addExpense(eq(TEST_USER), any(ExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Team lunch\",\"splitCount\":null}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).addExpense(eq(TEST_USER), captor.capture());
        assertThat(captor.getValue().getSplitCount()).isNull();
    }

    @Test
    @DisplayName("VAL-03 Test 3: Split count 1 is valid (boundary minimum)")
    void testSplitCount1Accepted() throws Exception {
        ExpenseResponse response = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(100.00))
                .description("Personal coffee")
                .build();

        when(expenseService.addExpense(eq(TEST_USER), any(ExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Personal coffee\",\"splitCount\":1}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).addExpense(eq(TEST_USER), captor.capture());
        assertThat(captor.getValue().getSplitCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 10, 50})
    @DisplayName("VAL-03 Test 4: Normal split counts (2, 4, 10, 50) are accepted")
    void testNormalSplitCountsAccepted(int splitCount) throws Exception {
        ExpenseResponse response = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(25.00))
                .description("Dinner (split between " + splitCount + " people)")
                .build();

        when(expenseService.addExpense(eq(TEST_USER), any(ExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Dinner\",\"splitCount\":" + splitCount + "}"))
                .andExpect(status().isOk());

        verify(expenseService).addExpense(eq(TEST_USER), any(ExpenseRequest.class));
    }

    @Test
    @DisplayName("VAL-03 Test 5: Maximum allowed split count (100) is accepted")
    void testMaxSplitCount100Accepted() throws Exception {
        ExpenseResponse response = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(BigDecimal.valueOf(10.00))
                .description("College fest (split between 100 people)")
                .build();

        when(expenseService.addExpense(eq(TEST_USER), any(ExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000.00,\"description\":\"College fest\",\"splitCount\":100}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExpenseRequest> captor = ArgumentCaptor.forClass(ExpenseRequest.class);
        verify(expenseService).addExpense(eq(TEST_USER), captor.capture());
        assertThat(captor.getValue().getSplitCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("VAL-03 Test 6: Split count 0 is rejected with 400 Bad Request")
    void testSplitCount0Rejected() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Dinner\",\"splitCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("splitCount: Split count must be at least 1"));

        verify(expenseService, never()).addExpense(anyString(), any(ExpenseRequest.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10, -100})
    @DisplayName("VAL-03 Test 7: Negative split counts are rejected with 400 Bad Request")
    void testNegativeSplitCountRejected(int negativeCount) throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Dinner\",\"splitCount\":" + negativeCount + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("splitCount: Split count must be at least 1"));

        verify(expenseService, never()).addExpense(anyString(), any(ExpenseRequest.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {101, 105, 1000, 1000000})
    @DisplayName("VAL-03 Test 8: Split counts above maximum 100 are rejected with 400 Bad Request")
    void testSplitCountAbove100Rejected(int excessiveCount) throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Dinner\",\"splitCount\":" + excessiveCount + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("splitCount: Split count cannot exceed 100"));

        verify(expenseService, never()).addExpense(anyString(), any(ExpenseRequest.class));
    }

    @Test
    @DisplayName("VAL-03 Test 9: Non-numeric splitCount returns safe 400 error")
    void testNonNumericSplitCountRejected() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"description\":\"Dinner\",\"splitCount\":\"four\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));

        verify(expenseService, never()).addExpense(anyString(), any(ExpenseRequest.class));
    }

    // =========================================================================
    // 2. Service-Level Financial Calculation & AI Invariant Tests
    // =========================================================================

    @Test
    @DisplayName("VAL-03 Test 10: Service does not alter amount or description when splitCount is null or 1")
    void testServiceCalculation_nullOrOne_unmodified() {
        ExpenseService realService = new ExpenseService(expenseRepository, userRepository, categoryRepository, aiService);

        User mockUser = User.builder().email(TEST_USER).build();
        when(userRepository.findByEmail(TEST_USER)).thenReturn(Optional.of(mockUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        AiCategorizationResult mockAi = new AiCategorizationResult();
        mockAi.setCategory("Food");
        mockAi.setConfidence(0.9);
        mockAi.setWasteful(false);
        when(aiService.categorizeExpense(anyString(), any(), any(), any()))
                .thenReturn(mockAi);

        Category foodCat = Category.builder().name("Food").build();
        when(categoryRepository.findByNameIgnoreCase("Food")).thenReturn(Optional.of(foodCat));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When splitCount is null
        ExpenseRequest reqNull = new ExpenseRequest();
        reqNull.setAmount(BigDecimal.valueOf(500.00));
        reqNull.setDescription("Lunch");
        reqNull.setSplitCount(null);

        ExpenseResponse respNull = realService.addExpense(TEST_USER, reqNull);
        assertThat(respNull.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(respNull.getDescription()).isEqualTo("Lunch");

        // When splitCount is 1
        ExpenseRequest reqOne = new ExpenseRequest();
        reqOne.setAmount(BigDecimal.valueOf(500.00));
        reqOne.setDescription("Lunch");
        reqOne.setSplitCount(1);

        ExpenseResponse respOne = realService.addExpense(TEST_USER, reqOne);
        assertThat(respOne.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(respOne.getDescription()).isEqualTo("Lunch");
    }

    @Test
    @DisplayName("VAL-03 Test 11: Service correctly divides amount and annotates description for splitCount 4")
    void testServiceCalculation_splitCount4_dividesAmount() {
        ExpenseService realService = new ExpenseService(expenseRepository, userRepository, categoryRepository, aiService);

        User mockUser = User.builder().email(TEST_USER).build();
        when(userRepository.findByEmail(TEST_USER)).thenReturn(Optional.of(mockUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        AiCategorizationResult mockAi = new AiCategorizationResult();
        mockAi.setCategory("Food");
        mockAi.setConfidence(0.95);
        mockAi.setWasteful(false);
        when(aiService.categorizeExpense(anyString(), any(), any(), any()))
                .thenReturn(mockAi);

        Category foodCat = Category.builder().name("Food").build();
        when(categoryRepository.findByNameIgnoreCase("Food")).thenReturn(Optional.of(foodCat));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExpenseRequest req = new ExpenseRequest();
        req.setAmount(BigDecimal.valueOf(800.00));
        req.setDescription("Pizza party");
        req.setSplitCount(4);

        ExpenseResponse resp = realService.addExpense(TEST_USER, req);
        assertThat(resp.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
        assertThat(resp.getDescription()).isEqualTo("Pizza party (split between 4 people)");

        // Verify AI was called with per-person share (200.00)
        verify(aiService).categorizeExpense(
                eq("Pizza party"),
                argThat(a -> a != null && a.compareTo(BigDecimal.valueOf(200)) == 0),
                any(),
                any());
    }

    @Test
    @DisplayName("VAL-03 Test 12: Service directly rejects splitCount 0, negative, and > 100 with IllegalArgumentException")
    void testServiceDirectValidation_rejectsOutOfBounds() {
        ExpenseService realService = new ExpenseService(expenseRepository, userRepository, categoryRepository, aiService);

        User mockUser = User.builder().email(TEST_USER).build();
        when(userRepository.findByEmail(TEST_USER)).thenReturn(Optional.of(mockUser));

        ExpenseRequest req0 = new ExpenseRequest();
        req0.setAmount(BigDecimal.valueOf(100.00));
        req0.setDescription("Lunch");
        req0.setSplitCount(0);

        assertThatThrownBy(() -> realService.addExpense(TEST_USER, req0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Split count must be at least 1");

        ExpenseRequest reqNeg = new ExpenseRequest();
        reqNeg.setAmount(BigDecimal.valueOf(100.00));
        reqNeg.setDescription("Lunch");
        reqNeg.setSplitCount(-5);

        assertThatThrownBy(() -> realService.addExpense(TEST_USER, reqNeg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Split count must be at least 1");

        ExpenseRequest req101 = new ExpenseRequest();
        req101.setAmount(BigDecimal.valueOf(100.00));
        req101.setDescription("Lunch");
        req101.setSplitCount(101);

        assertThatThrownBy(() -> realService.addExpense(TEST_USER, req101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Split count cannot exceed 100");

        verifyNoInteractions(expenseRepository);
        verifyNoInteractions(aiService);
    }
}
