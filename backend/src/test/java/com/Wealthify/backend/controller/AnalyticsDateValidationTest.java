package com.Wealthify.backend.controller;

import com.Wealthify.backend.dto.DailySummaryResponse;
import com.Wealthify.backend.dto.ExpenseResponse;
import com.Wealthify.backend.dto.MonthlySummaryResponse;
import com.Wealthify.backend.dto.StockRecommendationResponse;
import com.Wealthify.backend.dto.WastefulAnalysisResponse;
import com.Wealthify.backend.exception.GlobalExceptionHandler;
import com.Wealthify.backend.service.AnalyticsService;
import com.Wealthify.backend.service.AuthService;
import com.Wealthify.backend.service.ExpenseService;
import com.Wealthify.backend.service.StockAdvisorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnalyticsDateValidationTest {

    private static final String TEST_USER = "user@example.com";

    @Mock
    private ExpenseService expenseService;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private StockAdvisorService stockAdvisorService;

    @Mock
    private AuthService authService;

    private UserDetails userDetails;
    private MockMvc mockMvc;

    private ExpenseController expenseController;
    private AnalyticsController analyticsController;
    private InsightController insightController;

    @BeforeEach
    void setUp() {
        userDetails = new User(TEST_USER, "password", Collections.emptyList());

        expenseController = new ExpenseController(expenseService);
        analyticsController = new AnalyticsController(analyticsService);
        insightController = new InsightController(stockAdvisorService, authService);

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
                return userDetails;
            }
        };

        mockMvc = MockMvcBuilders
                .standaloneSetup(expenseController, analyticsController, insightController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // =========================================================================
    // ExpenseController: /api/expenses/month/{year}/{month}
    // =========================================================================

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 6, 11, 12})
    @DisplayName("ExpenseController: Valid month boundaries (1..12) return 200 OK")
    void testExpenseByMonth_validMonths_returns200(int month) throws Exception {
        when(expenseService.getExpensesByMonth(eq(TEST_USER), eq(month), eq(2026)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/month/2026/" + month))
                .andExpect(status().isOk());

        verify(expenseService).getExpensesByMonth(eq(TEST_USER), eq(month), eq(2026));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1, 99})
    @DisplayName("ExpenseController: Invalid months return 400 Bad Request")
    void testExpenseByMonth_invalidMonths_returns400(int month) throws Exception {
        mockMvc.perform(get("/api/expenses/month/2026/" + month))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Month must be between 1 and 12"));

        verifyNoInteractions(expenseService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1970, 2000, 2026, 2099, 2100})
    @DisplayName("ExpenseController: Valid year boundaries (1970..2100) return 200 OK")
    void testExpenseByMonth_validYears_returns200(int year) throws Exception {
        when(expenseService.getExpensesByMonth(eq(TEST_USER), eq(10), eq(year)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/month/" + year + "/10"))
                .andExpect(status().isOk());

        verify(expenseService).getExpensesByMonth(eq(TEST_USER), eq(10), eq(year));
    }

    @ParameterizedTest
    @ValueSource(ints = {1969, 2101, 0, -1, 9999})
    @DisplayName("ExpenseController: Out-of-bounds years return 400 Bad Request")
    void testExpenseByMonth_invalidYears_returns400(int year) throws Exception {
        mockMvc.perform(get("/api/expenses/month/" + year + "/10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Year must be between 1970 and 2100"));

        verifyNoInteractions(expenseService);
    }

    @Test
    @DisplayName("ExpenseController: Non-numeric month returns 400 Bad Request")
    void testExpenseByMonth_nonNumericMonth_returns400() throws Exception {
        mockMvc.perform(get("/api/expenses/month/2026/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: month"));

        verifyNoInteractions(expenseService);
    }

    @Test
    @DisplayName("ExpenseController: Non-numeric year returns 400 Bad Request")
    void testExpenseByMonth_nonNumericYear_returns400() throws Exception {
        mockMvc.perform(get("/api/expenses/month/xyz/10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: year"));

        verifyNoInteractions(expenseService);
    }

    @Test
    @DisplayName("ExpenseController: Valid date path returns 200 OK")
    void testExpenseByDate_validDate_returns200() throws Exception {
        when(expenseService.getExpensesByDate(eq(TEST_USER), eq(LocalDate.parse("2026-10-04"))))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/expenses/date/2026-10-04"))
                .andExpect(status().isOk());

        verify(expenseService).getExpensesByDate(eq(TEST_USER), eq(LocalDate.parse("2026-10-04")));
    }

    @Test
    @DisplayName("ExpenseController: Malformed date path returns 400 Bad Request")
    void testExpenseByDate_malformedDate_returns400() throws Exception {
        mockMvc.perform(get("/api/expenses/date/invalid-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid date format. Expected YYYY-MM-DD"));

        verifyNoInteractions(expenseService);
    }

    // =========================================================================
    // AnalyticsController: /api/analytics/monthly
    // =========================================================================

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 12})
    @DisplayName("AnalyticsController: Valid month params (1..12) return 200 OK")
    void testAnalyticsMonthly_validMonths_returns200(int month) throws Exception {
        when(analyticsService.getMonthlySummary(eq(TEST_USER), eq(month), eq(2026)))
                .thenReturn(MonthlySummaryResponse.builder().month(month).year(2026).build());

        mockMvc.perform(get("/api/analytics/monthly")
                        .param("month", String.valueOf(month))
                        .param("year", "2026"))
                .andExpect(status().isOk());

        verify(analyticsService).getMonthlySummary(eq(TEST_USER), eq(month), eq(2026));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1, 100})
    @DisplayName("AnalyticsController: Invalid months return 400 Bad Request")
    void testAnalyticsMonthly_invalidMonths_returns400(int month) throws Exception {
        mockMvc.perform(get("/api/analytics/monthly")
                        .param("month", String.valueOf(month))
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Month must be between 1 and 12"));

        verifyNoInteractions(analyticsService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1970, 2026, 2100})
    @DisplayName("AnalyticsController: Valid year params (1970..2100) return 200 OK")
    void testAnalyticsMonthly_validYears_returns200(int year) throws Exception {
        when(analyticsService.getMonthlySummary(eq(TEST_USER), eq(10), eq(year)))
                .thenReturn(MonthlySummaryResponse.builder().month(10).year(year).build());

        mockMvc.perform(get("/api/analytics/monthly")
                        .param("month", "10")
                        .param("year", String.valueOf(year)))
                .andExpect(status().isOk());

        verify(analyticsService).getMonthlySummary(eq(TEST_USER), eq(10), eq(year));
    }

    @ParameterizedTest
    @ValueSource(ints = {1969, 2101, -2026, 0})
    @DisplayName("AnalyticsController: Out-of-bounds years return 400 Bad Request")
    void testAnalyticsMonthly_invalidYears_returns400(int year) throws Exception {
        mockMvc.perform(get("/api/analytics/monthly")
                        .param("month", "10")
                        .param("year", String.valueOf(year)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Year must be between 1970 and 2100"));

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("AnalyticsController: Non-numeric month param returns 400 Bad Request")
    void testAnalyticsMonthly_nonNumericMonth_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/monthly")
                        .param("month", "not-a-number")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: month"));

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("AnalyticsController: Non-numeric year param returns 400 Bad Request")
    void testAnalyticsMonthly_nonNumericYear_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/monthly")
                        .param("month", "10")
                        .param("year", "not-a-year"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: year"));

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("AnalyticsController: Missing params default to current month and year")
    void testAnalyticsMonthly_defaultParams_returns200() throws Exception {
        int expectedMonth = LocalDate.now().getMonthValue();
        int expectedYear = LocalDate.now().getYear();

        when(analyticsService.getMonthlySummary(eq(TEST_USER), eq(expectedMonth), eq(expectedYear)))
                .thenReturn(MonthlySummaryResponse.builder().month(expectedMonth).year(expectedYear).build());

        mockMvc.perform(get("/api/analytics/monthly"))
                .andExpect(status().isOk());

        verify(analyticsService).getMonthlySummary(eq(TEST_USER), eq(expectedMonth), eq(expectedYear));
    }

    // =========================================================================
    // InsightController: /api/insights/wasteful & /api/stocks/recommend
    // =========================================================================

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 12})
    @DisplayName("InsightController: Valid months return 200 OK for wasteful analysis")
    void testWastefulAnalysis_validMonths_returns200(int month) throws Exception {
        when(stockAdvisorService.getWastefulAnalysis(eq(TEST_USER), eq(month), eq(2026)))
                .thenReturn(WastefulAnalysisResponse.builder().build());

        mockMvc.perform(get("/api/insights/wasteful")
                        .param("month", String.valueOf(month))
                        .param("year", "2026"))
                .andExpect(status().isOk());

        verify(stockAdvisorService).getWastefulAnalysis(eq(TEST_USER), eq(month), eq(2026));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1})
    @DisplayName("InsightController: Invalid months return 400 Bad Request for wasteful analysis")
    void testWastefulAnalysis_invalidMonths_returns400(int month) throws Exception {
        mockMvc.perform(get("/api/insights/wasteful")
                        .param("month", String.valueOf(month))
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Month must be between 1 and 12"));

        verifyNoInteractions(stockAdvisorService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1969, 2101})
    @DisplayName("InsightController: Invalid years return 400 Bad Request for wasteful analysis")
    void testWastefulAnalysis_invalidYears_returns400(int year) throws Exception {
        mockMvc.perform(get("/api/insights/wasteful")
                        .param("month", "10")
                        .param("year", String.valueOf(year)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Year must be between 1970 and 2100"));

        verifyNoInteractions(stockAdvisorService);
    }

    @Test
    @DisplayName("InsightController: Wasteful analysis defaults to current month and year when omitted")
    void testWastefulAnalysis_defaultParams_returns200() throws Exception {
        int expectedMonth = LocalDate.now().getMonthValue();
        int expectedYear = LocalDate.now().getYear();

        when(stockAdvisorService.getWastefulAnalysis(eq(TEST_USER), eq(expectedMonth), eq(expectedYear)))
                .thenReturn(WastefulAnalysisResponse.builder().build());

        mockMvc.perform(get("/api/insights/wasteful"))
                .andExpect(status().isOk());

        verify(stockAdvisorService).getWastefulAnalysis(eq(TEST_USER), eq(expectedMonth), eq(expectedYear));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6, 12})
    @DisplayName("InsightController: Valid months return 200 OK for stock recommendations")
    void testStockRecommendations_validMonths_returns200(int month) throws Exception {
        when(stockAdvisorService.getStockRecommendations(eq(TEST_USER), eq(month), eq(2026)))
                .thenReturn(StockRecommendationResponse.builder().build());

        mockMvc.perform(get("/api/stocks/recommend")
                        .param("month", String.valueOf(month))
                        .param("year", "2026"))
                .andExpect(status().isOk());

        verify(stockAdvisorService).getStockRecommendations(eq(TEST_USER), eq(month), eq(2026));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1})
    @DisplayName("InsightController: Invalid months return 400 Bad Request for stock recommendations")
    void testStockRecommendations_invalidMonths_returns400(int month) throws Exception {
        mockMvc.perform(get("/api/stocks/recommend")
                        .param("month", String.valueOf(month))
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Month must be between 1 and 12"));

        verifyNoInteractions(stockAdvisorService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1969, 2101})
    @DisplayName("InsightController: Invalid years return 400 Bad Request for stock recommendations")
    void testStockRecommendations_invalidYears_returns400(int year) throws Exception {
        mockMvc.perform(get("/api/stocks/recommend")
                        .param("month", "10")
                        .param("year", String.valueOf(year)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Year must be between 1970 and 2100"));

        verifyNoInteractions(stockAdvisorService);
    }

    @Test
    @DisplayName("InsightController: Stock recommendations defaults to current month and year when omitted")
    void testStockRecommendations_defaultParams_returns200() throws Exception {
        int expectedMonth = LocalDate.now().getMonthValue();
        int expectedYear = LocalDate.now().getYear();

        when(stockAdvisorService.getStockRecommendations(eq(TEST_USER), eq(expectedMonth), eq(expectedYear)))
                .thenReturn(StockRecommendationResponse.builder().build());

        mockMvc.perform(get("/api/stocks/recommend"))
                .andExpect(status().isOk());

        verify(stockAdvisorService).getStockRecommendations(eq(TEST_USER), eq(expectedMonth), eq(expectedYear));
    }

    // =========================================================================
    // Direct Unit Testing (Java boundary verification)
    // =========================================================================

    @Test
    @DisplayName("Direct unit test: Controllers throw IllegalArgumentException directly on invalid month/year")
    void testDirectControllerInvocations_throwIllegalArgumentException() {
        assertThatThrownBy(() -> expenseController.getByMonth(userDetails, 2026, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Month must be between 1 and 12");

        assertThatThrownBy(() -> expenseController.getByMonth(userDetails, 2026, 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Month must be between 1 and 12");

        assertThatThrownBy(() -> expenseController.getByMonth(userDetails, 1969, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Year must be between 1970 and 2100");

        assertThatThrownBy(() -> expenseController.getByMonth(userDetails, 2101, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Year must be between 1970 and 2100");

        assertThatThrownBy(() -> analyticsController.getMonthly(userDetails, 0, 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Month must be between 1 and 12");

        assertThatThrownBy(() -> analyticsController.getMonthly(userDetails, 13, 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Month must be between 1 and 12");

        assertThatThrownBy(() -> insightController.getWasteful(userDetails, 0, 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Month must be between 1 and 12");

        assertThatThrownBy(() -> insightController.getStockRecommendations(userDetails, 13, 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Month must be between 1 and 12");
    }
}
