package com.Wealthify.backend.service;

import com.Wealthify.backend.config.AbstractDatabaseIsolationTest;
import com.Wealthify.backend.dto.DailySummaryResponse;
import com.Wealthify.backend.dto.MonthlySummaryResponse;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.CategoryRepository;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.UserRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OPT-01 End-to-End Integration Test Suite:
 * Runs against an isolated Zonky embedded PostgreSQL database.
 * Verifies:
 * 1. Monthly analytics calculation against real database entities and queries.
 * 2. Elimination of per-day AI calls (0 invocations of aiService.generateDailySummary).
 * 3. Exact mathematical results across multiple active days, single active days, and empty months.
 * 4. User data isolation (expenses of another user are strictly excluded).
 * 5. Date boundary isolation (expenses in adjacent months are strictly excluded).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class AnalyticsMonthlyOptimizationIntegrationTest extends AbstractDatabaseIsolationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockitoBean
    private AiService aiService;

    private User testUser;
    private User otherUser;
    private Category foodCategory;
    private Category transportCategory;
    private Category entertainmentCategory;

    private final List<UUID> createdExpenseIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .name("Analytics Test User")
                .email("analytics_tester_" + UUID.randomUUID() + "@example.com")
                .password("$2a$10$dummyHashForAnalyticsIntegrationTest")
                .monthlyIncome(BigDecimal.valueOf(80000.00))
                .build());
        createdUserIds.add(testUser.getId());

        otherUser = userRepository.save(User.builder()
                .name("Other User")
                .email("other_user_" + UUID.randomUUID() + "@example.com")
                .password("$2a$10$dummyHashForAnalyticsIntegrationTest")
                .monthlyIncome(BigDecimal.valueOf(60000.00))
                .build());
        createdUserIds.add(otherUser.getId());

        foodCategory = categoryRepository.findByNameIgnoreCase("Food")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("Food").type("NEED").isEssential(true).build()));

        transportCategory = categoryRepository.findByNameIgnoreCase("Transport")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("Transport").type("NEED").isEssential(true).build()));

        entertainmentCategory = categoryRepository.findByNameIgnoreCase("Entertainment")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("Entertainment").type("WANT").isEssential(false).build()));

        // Default mock behaviors for top-level AI summaries to prevent external calls
        when(aiService.generateMonthlySummary(any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn("Mocked monthly AI summary for test");
        when(aiService.generateSpendingTips(any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(List.of("Mocked Tip 1", "Mocked Tip 2", "Mocked Tip 3"));
        when(aiService.generateDailySummary(any(), any(), any(), any(), anyInt()))
                .thenReturn("Mocked daily AI summary");
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdExpenseIds) {
            try {
                expenseRepository.deleteById(id);
            } catch (Exception ignored) {}
        }
        for (UUID id : createdUserIds) {
            try {
                userRepository.deleteById(id);
            } catch (Exception ignored) {}
        }
    }

    private Expense createAndSaveExpense(User user, BigDecimal amount, String description,
                                         Category category, LocalDate date, boolean wasteful) {
        Expense expense = expenseRepository.save(Expense.builder()
                .user(user)
                .amount(amount)
                .description(description)
                .category(category)
                .expenseDate(date)
                .isFlaggedWasteful(wasteful)
                .build());
        createdExpenseIds.add(expense.getId());
        return expense;
    }

    @Test
    @DisplayName("Empty Month: Returns accurate zero stats from real PostgreSQL without invoking daily AI")
    void testEmptyMonth_ZeroDailyAiCalls() {
        MonthlySummaryResponse response = analyticsService.getMonthlySummary(testUser.getEmail(), 10, 2026);

        assertThat(response).isNotNull();
        assertThat(response.getMonth()).isEqualTo(10);
        assertThat(response.getYear()).isEqualTo(2026);
        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalIncome()).isEqualByComparingTo(BigDecimal.valueOf(80000.00));
        assertThat(response.getSavingsAmount()).isEqualByComparingTo(BigDecimal.valueOf(80000.00));
        assertThat(response.getSavingsPercentage()).isEqualTo(100.0);
        assertThat(response.getTotalTransactions()).isEqualTo(0);
        assertThat(response.getDailyBreakdown()).isEmpty();
        assertThat(response.getAiSummary()).isEqualTo("No expenses recorded this month.");

        // Verify zero invocations of daily AI
        verify(aiService, never()).generateDailySummary(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Single Day: Correctly aggregates from database and executes ZERO daily AI calls")
    void testSingleDay_DatabaseAggregation_ZeroDailyAiCalls() {
        LocalDate targetDate = LocalDate.of(2026, 10, 8);
        createAndSaveExpense(testUser, BigDecimal.valueOf(350.00), "Dinner", foodCategory, targetDate, false);

        MonthlySummaryResponse response = analyticsService.getMonthlySummary(testUser.getEmail(), 10, 2026);

        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(350.00));
        assertThat(response.getTotalTransactions()).isEqualTo(1);
        assertThat(response.getDailyBreakdown()).hasSize(1);

        DailySummaryResponse day = response.getDailyBreakdown().get(0);
        assertThat(day.getDate()).isEqualTo(targetDate);
        assertThat(day.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(350.00));
        assertThat(day.getSpendingByCategory()).containsKey("Food");
        assertThat(day.getSpendingByCategory().get("Food")).isEqualByComparingTo("350.00");
        assertThat(day.getAiSummary()).isNull();

        // Must NOT call daily AI
        verify(aiService, never()).generateDailySummary(any(), any(), any(), any(), anyInt());
        verify(aiService, times(1)).generateMonthlySummary(any(), eq(10), eq(2026), any(), any(), any());
    }

    @Test
    @DisplayName("Multiple Active Days: Validates chronological ordering, date boundaries, tenant isolation, and ZERO daily AI calls")
    void testMultipleActiveDays_ChronologicalOrder_TenantIsolation_ZeroDailyAiCalls() {
        // Expenses for testUser in October 2026 (target month)
        LocalDate day1 = LocalDate.of(2026, 10, 2);
        LocalDate day2 = LocalDate.of(2026, 10, 11);
        LocalDate day3 = LocalDate.of(2026, 10, 19);
        LocalDate day4 = LocalDate.of(2026, 10, 27);

        createAndSaveExpense(testUser, BigDecimal.valueOf(200.00), "Metro commute", transportCategory, day1, false);
        createAndSaveExpense(testUser, BigDecimal.valueOf(150.00), "Lunch", foodCategory, day1, false);
        createAndSaveExpense(testUser, BigDecimal.valueOf(1200.00), "Concert ticket", entertainmentCategory, day2, true);
        createAndSaveExpense(testUser, BigDecimal.valueOf(450.00), "Groceries", foodCategory, day3, false);
        createAndSaveExpense(testUser, BigDecimal.valueOf(800.00), "Weekend party", entertainmentCategory, day4, true);

        // Expense in a DIFFERENT month (September 2026) -> should be excluded
        createAndSaveExpense(testUser, BigDecimal.valueOf(5000.00), "September rent", transportCategory, LocalDate.of(2026, 9, 30), false);

        // Expense in a DIFFERENT month (November 2026) -> should be excluded
        createAndSaveExpense(testUser, BigDecimal.valueOf(3000.00), "November flight", transportCategory, LocalDate.of(2026, 11, 1), false);

        // Expense belonging to OTHER user in October 2026 -> should be excluded (Tenant Isolation)
        createAndSaveExpense(otherUser, BigDecimal.valueOf(9999.00), "Other user laptop", entertainmentCategory, day2, true);

        MonthlySummaryResponse response = analyticsService.getMonthlySummary(testUser.getEmail(), 10, 2026);

        // Total spent: 200 + 150 + 1200 + 450 + 800 = 2800.00
        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(2800.00));
        assertThat(response.getTotalTransactions()).isEqualTo(5);

        // Wasteful amount: 1200 + 800 = 2000.00
        assertThat(response.getWastefulAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000.00));

        // Category breakdown: Food = 600, Transport = 200, Entertainment = 2000
        assertThat(response.getSpendingByCategory().get("Food")).isEqualByComparingTo("600.00");
        assertThat(response.getSpendingByCategory().get("Transport")).isEqualByComparingTo("200.00");
        assertThat(response.getSpendingByCategory().get("Entertainment")).isEqualByComparingTo("2000.00");

        // Daily breakdown should have exactly 4 days
        assertThat(response.getDailyBreakdown()).hasSize(4);

        // Strictly chronological ordering
        List<LocalDate> dates = response.getDailyBreakdown().stream()
                .map(DailySummaryResponse::getDate)
                .toList();
        assertThat(dates).containsExactly(day1, day2, day3, day4);

        // Day 1: 2 transactions (200 + 150 = 350)
        DailySummaryResponse day1Summary = response.getDailyBreakdown().get(0);
        assertThat(day1Summary.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(350.00));
        assertThat(day1Summary.getTotalTransactions()).isEqualTo(2);
        assertThat(day1Summary.getAiSummary()).isNull();

        // Day 2: 1 wasteful transaction (1200)
        DailySummaryResponse day2Summary = response.getDailyBreakdown().get(1);
        assertThat(day2Summary.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(1200.00));
        assertThat(day2Summary.getWastefulCount()).isEqualTo(1);
        assertThat(day2Summary.getWastefulAmount()).isEqualByComparingTo(BigDecimal.valueOf(1200.00));
        assertThat(day2Summary.getAiSummary()).isNull();

        // CRITICAL PERFORMANCE ASSERTION:
        // In the unoptimized codebase, 4 days with expenses would trigger 4 separate aiService.generateDailySummary calls.
        // In the optimized codebase, it must be exactly ZERO.
        verify(aiService, never()).generateDailySummary(any(), any(), any(), any(), anyInt());

        // Top-level monthly summaries are called once
        verify(aiService, times(1)).generateMonthlySummary(any(), eq(10), eq(2026), any(), any(), any());
        verify(aiService, times(1)).generateSpendingTips(any(), eq(10), eq(2026), any(), any(), any());
    }

    @Test
    @DisplayName("Standalone /api/analytics/daily continues to call AI service with real database fixture")
    void testStandaloneDailyEndpointStillCallsAiService() {
        LocalDate date = LocalDate.of(2026, 10, 11);
        createAndSaveExpense(testUser, BigDecimal.valueOf(500.00), "Lunch with client", foodCategory, date, false);

        DailySummaryResponse dailyResponse = analyticsService.getDailySummary(testUser.getEmail(), date);

        assertThat(dailyResponse).isNotNull();
        assertThat(dailyResponse.getDate()).isEqualTo(date);
        assertThat(dailyResponse.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(dailyResponse.getAiSummary()).isEqualTo("Mocked daily AI summary");

        // Verify standalone daily endpoint DID call generateDailySummary once
        verify(aiService, times(1)).generateDailySummary(eq(testUser.getId()), eq(date), any(), anyMap(), eq(0));
    }
}
