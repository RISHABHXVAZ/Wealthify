package com.Wealthify.backend.controller;

import com.Wealthify.backend.security.UserPrincipal;
import com.Wealthify.backend.service.AnalyticsService;
import com.Wealthify.backend.service.AuthService;
import com.Wealthify.backend.service.ExpenseService;
import com.Wealthify.backend.service.GoalService;
import com.Wealthify.backend.service.StockAdvisorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserLookupControllerDispatchTest {

    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private GoalService goalService;
    @Mock
    private StockAdvisorService stockAdvisorService;
    @Mock
    private AuthService authService;

    private final UUID testId = UUID.randomUUID();
    private final UserPrincipal principal = new UserPrincipal(
            testId, "Test User", "test@wealthify.test", "pass",
            BigDecimal.valueOf(50000), BigDecimal.valueOf(20), BigDecimal.valueOf(30),
            Collections.emptyList()
    );

    private final UserDetails standardUserDetails = new User(
            "test@wealthify.test", "pass", Collections.emptyList()
    );

    @Test
    @DisplayName("AnalyticsController delegates to UserPrincipal overload when authenticated with UserPrincipal")
    void testAnalyticsControllerWithUserPrincipal() {
        AnalyticsController controller = new AnalyticsController(analyticsService);
        LocalDate date = LocalDate.of(2026, 10, 14);

        controller.getDaily(principal, date);
        verify(analyticsService).getDailySummary(principal, date);

        controller.getMonthly(principal, 10, 2026);
        verify(analyticsService).getMonthlySummary(principal, 10, 2026);
    }

    @Test
    @DisplayName("AnalyticsController delegates to String username overload when standard UserDetails provided")
    void testAnalyticsControllerWithStandardUserDetails() {
        AnalyticsController controller = new AnalyticsController(analyticsService);
        LocalDate date = LocalDate.of(2026, 10, 14);

        controller.getDaily(standardUserDetails, date);
        verify(analyticsService).getDailySummary("test@wealthify.test", date);

        controller.getMonthly(standardUserDetails, 10, 2026);
        verify(analyticsService).getMonthlySummary("test@wealthify.test", 10, 2026);
    }

    @Test
    @DisplayName("ExpenseController delegates to UserPrincipal overload when authenticated with UserPrincipal")
    void testExpenseControllerWithUserPrincipal() {
        ExpenseController controller = new ExpenseController(expenseService);
        UUID expenseId = UUID.randomUUID();

        controller.getToday(principal);
        verify(expenseService).getExpensesByDate(eq(principal), any(LocalDate.class));
        reset(expenseService);

        LocalDate customDate = LocalDate.of(2026, 5, 20);
        controller.getByDate(principal, "2026-05-20");
        verify(expenseService).getExpensesByDate(principal, customDate);
        reset(expenseService);

        controller.getByMonth(principal, 2026, 10);
        verify(expenseService).getExpensesByMonth(principal, 10, 2026);
        reset(expenseService);

        controller.delete(principal, expenseId);
        verify(expenseService).deleteExpense(principal, expenseId);
    }

    @Test
    @DisplayName("ExpenseController delegates to String username overload when standard UserDetails provided")
    void testExpenseControllerWithStandardUserDetails() {
        ExpenseController controller = new ExpenseController(expenseService);
        UUID expenseId = UUID.randomUUID();

        controller.getToday(standardUserDetails);
        verify(expenseService).getExpensesByDate(eq("test@wealthify.test"), any(LocalDate.class));
        reset(expenseService);

        LocalDate customDate = LocalDate.of(2026, 5, 20);
        controller.getByDate(standardUserDetails, "2026-05-20");
        verify(expenseService).getExpensesByDate("test@wealthify.test", customDate);
        reset(expenseService);

        controller.getByMonth(standardUserDetails, 2026, 10);
        verify(expenseService).getExpensesByMonth("test@wealthify.test", 10, 2026);
        reset(expenseService);

        controller.delete(standardUserDetails, expenseId);
        verify(expenseService).deleteExpense("test@wealthify.test", expenseId);
    }

    @Test
    @DisplayName("GoalController delegates to UserPrincipal overload when authenticated with UserPrincipal")
    void testGoalControllerWithUserPrincipal() {
        GoalController controller = new GoalController(goalService);
        UUID goalId = UUID.randomUUID();

        controller.getBudget(principal);
        verify(goalService).getBudgetAllocation(principal);

        controller.getGoals(principal);
        verify(goalService).getGoals(principal);

        controller.deleteGoal(principal, goalId);
        verify(goalService).deleteGoal(principal, goalId);
    }

    @Test
    @DisplayName("GoalController delegates to String username overload when standard UserDetails provided")
    void testGoalControllerWithStandardUserDetails() {
        GoalController controller = new GoalController(goalService);
        UUID goalId = UUID.randomUUID();

        controller.getBudget(standardUserDetails);
        verify(goalService).getBudgetAllocation("test@wealthify.test");

        controller.getGoals(standardUserDetails);
        verify(goalService).getGoals("test@wealthify.test");

        controller.deleteGoal(standardUserDetails, goalId);
        verify(goalService).deleteGoal("test@wealthify.test", goalId);
    }

    @Test
    @DisplayName("InsightController delegates to UserPrincipal overload when authenticated with UserPrincipal")
    void testInsightControllerWithUserPrincipal() {
        InsightController controller = new InsightController(stockAdvisorService, authService);

        controller.getWasteful(principal, 10, 2026);
        verify(stockAdvisorService).getWastefulAnalysis(principal, 10, 2026);

        controller.getStockRecommendations(principal, 10, 2026);
        verify(stockAdvisorService).getStockRecommendations(principal, 10, 2026);
    }

    @Test
    @DisplayName("InsightController delegates to String username overload when standard UserDetails provided")
    void testInsightControllerWithStandardUserDetails() {
        InsightController controller = new InsightController(stockAdvisorService, authService);

        controller.getWasteful(standardUserDetails, 10, 2026);
        verify(stockAdvisorService).getWastefulAnalysis("test@wealthify.test", 10, 2026);

        controller.getStockRecommendations(standardUserDetails, 10, 2026);
        verify(stockAdvisorService).getStockRecommendations("test@wealthify.test", 10, 2026);
    }
}
