package com.Wealthify.backend.service;

import com.Wealthify.backend.dto.DailySummaryResponse;
import com.Wealthify.backend.dto.MonthlySummaryResponse;
import com.Wealthify.backend.entity.Category;
import com.Wealthify.backend.entity.Expense;
import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.ExpenseRepository;
import com.Wealthify.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OPT-01 Unit Regression Tests:
 * Verifies that AnalyticsService.getMonthlySummary eliminates unnecessary per-day AI calls
 * while preserving mathematical accuracy, chronological daily ordering, schema compatibility,
 * and standalone daily AI summary generation.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceOptimizationUnitTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiService aiService;

    @InjectMocks
    private AnalyticsService analyticsService;

    private User testUser;
    private Category foodCategory;
    private Category transportCategory;
    private Category shoppingCategory;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .name("Test User")
                .email("test.user@example.com")
                .monthlyIncome(BigDecimal.valueOf(50000.00))
                .build();

        foodCategory = Category.builder()
                .id(1)
                .name("Food")
                .type("NEED")
                .isEssential(true)
                .build();

        transportCategory = Category.builder()
                .id(2)
                .name("Transport")
                .type("NEED")
                .isEssential(true)
                .build();

        shoppingCategory = Category.builder()
                .id(3)
                .name("Shopping")
                .type("WANT")
                .isEssential(false)
                .build();
    }

    @Test
    @DisplayName("Empty month: computes zero totals and does not invoke daily or monthly AI summaries")
    void testMonthlySummary_EmptyMonth_ZeroAiDailyCalls() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
                eq(testUser), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(Collections.emptyList());
        when(aiService.generateSpendingTips(eq(testUser.getId()), eq(10), eq(2026), anyMap(), any(), any()))
                .thenReturn(List.of("Tip 1", "Tip 2", "Tip 3"));

        MonthlySummaryResponse response = analyticsService.getMonthlySummary(testUser.getEmail(), 10, 2026);

        assertThat(response).isNotNull();
        assertThat(response.getMonth()).isEqualTo(10);
        assertThat(response.getYear()).isEqualTo(2026);
        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalIncome()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(response.getSavingsAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(response.getSavingsPercentage()).isEqualTo(100.0);
        assertThat(response.getTotalTransactions()).isEqualTo(0);
        assertThat(response.getDailyBreakdown()).isEmpty();
        assertThat(response.getSpendingByCategory()).isEmpty();
        assertThat(response.getWastefulAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getWastefulPercentage()).isEqualTo(0.0);
        assertThat(response.getAiSummary()).isEqualTo("No expenses recorded this month.");
        assertThat(response.getAiTips()).containsExactly("Tip 1", "Tip 2", "Tip 3");

        // Verify zero per-day AI calls
        verify(aiService, never()).generateDailySummary(any(), any(), any(), any(), anyInt());
        verify(aiService, never()).generateMonthlySummary(any(), anyInt(), anyInt(), any(), any(), any());
        verify(aiService, times(1)).generateSpendingTips(eq(testUser.getId()), eq(10), eq(2026), anyMap(), any(), any());
    }

    @Test
    @DisplayName("Single active day: computes correct breakdown and does NOT call daily AI summary")
    void testMonthlySummary_SingleActiveDay_ZeroAiDailyCalls() {
        LocalDate expenseDate = LocalDate.of(2026, 10, 5);
        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .amount(BigDecimal.valueOf(250.00))
                .description("Lunch at canteen")
                .category(foodCategory)
                .expenseDate(expenseDate)
                .isFlaggedWasteful(false)
                .build();

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
                eq(testUser), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(List.of(expense));
        when(aiService.generateMonthlySummary(eq(testUser.getId()), eq(10), eq(2026), any(), any(), anyMap()))
                .thenReturn("Monthly overview text");
        when(aiService.generateSpendingTips(eq(testUser.getId()), eq(10), eq(2026), anyMap(), any(), any()))
                .thenReturn(List.of("Tip 1"));

        MonthlySummaryResponse response = analyticsService.getMonthlySummary(testUser.getEmail(), 10, 2026);

        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
        assertThat(response.getTotalTransactions()).isEqualTo(1);
        assertThat(response.getDailyBreakdown()).hasSize(1);

        DailySummaryResponse dailyItem = response.getDailyBreakdown().get(0);
        assertThat(dailyItem.getDate()).isEqualTo(expenseDate);
        assertThat(dailyItem.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(250.00));
        assertThat(dailyItem.getTotalTransactions()).isEqualTo(1);
        assertThat(dailyItem.getSpendingByCategory()).containsEntry("Food", BigDecimal.valueOf(250.00));
        assertThat(dailyItem.getWastefulCount()).isEqualTo(0);
        assertThat(dailyItem.getWastefulAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dailyItem.getAiSummary()).isNull(); // Crucial: per-day AI text is null in monthly breakdown

        // Crucial verification: generateDailySummary was NEVER invoked
        verify(aiService, never()).generateDailySummary(any(), any(), any(), any(), anyInt());
        verify(aiService, times(1)).generateMonthlySummary(eq(testUser.getId()), eq(10), eq(2026), any(), any(), anyMap());
        verify(aiService, times(1)).generateSpendingTips(eq(testUser.getId()), eq(10), eq(2026), anyMap(), any(), any());
    }

    @Test
    @DisplayName("Multiple active days: preserves chronological order, accurate totals, and executes ZERO daily AI calls")
    void testMonthlySummary_MultipleActiveDays_ChronologicalOrderAndZeroDailyAiCalls() {
        LocalDate day1 = LocalDate.of(2026, 10, 3);
        LocalDate day2 = LocalDate.of(2026, 10, 10);
        LocalDate day3 = LocalDate.of(2026, 10, 15);
        LocalDate day4 = LocalDate.of(2026, 10, 22);

        List<Expense> expenses = List.of(
                Expense.builder().id(UUID.randomUUID()).user(testUser).amount(BigDecimal.valueOf(100.00))
                        .description("Bus ticket").category(transportCategory).expenseDate(day1).isFlaggedWasteful(false).build(),
                Expense.builder().id(UUID.randomUUID()).user(testUser).amount(BigDecimal.valueOf(300.00))
                        .description("Dinner").category(foodCategory).expenseDate(day2).isFlaggedWasteful(false).build(),
                Expense.builder().id(UUID.randomUUID()).user(testUser).amount(BigDecimal.valueOf(1200.00))
                        .description("Expensive shoes").category(shoppingCategory).expenseDate(day3).isFlaggedWasteful(true).build(),
                Expense.builder().id(UUID.randomUUID()).user(testUser).amount(BigDecimal.valueOf(500.00))
                        .description("Grocery").category(foodCategory).expenseDate(day4).isFlaggedWasteful(false).build()
        );

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
                eq(testUser), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(expenses);
        when(aiService.generateMonthlySummary(eq(testUser.getId()), eq(10), eq(2026), any(), any(), anyMap()))
                .thenReturn("Comprehensive monthly summary");
        when(aiService.generateSpendingTips(eq(testUser.getId()), eq(10), eq(2026), anyMap(), any(), any()))
                .thenReturn(List.of("Tip A", "Tip B"));

        MonthlySummaryResponse response = analyticsService.getMonthlySummary(testUser.getEmail(), 10, 2026);

        // Overall monthly metrics
        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(2100.00));
        assertThat(response.getTotalTransactions()).isEqualTo(4);
        assertThat(response.getWastefulAmount()).isEqualByComparingTo(BigDecimal.valueOf(1200.00));
        assertThat(response.getSpendingByCategory())
                .containsEntry("Food", BigDecimal.valueOf(800.00))
                .containsEntry("Shopping", BigDecimal.valueOf(1200.00))
                .containsEntry("Transport", BigDecimal.valueOf(100.00));

        // Daily breakdown checks
        assertThat(response.getDailyBreakdown()).hasSize(4);

        // Verify strictly ascending chronological ordering
        List<LocalDate> breakdownDates = response.getDailyBreakdown().stream()
                .map(DailySummaryResponse::getDate)
                .toList();
        assertThat(breakdownDates).containsExactly(day1, day2, day3, day4);

        // Verify every daily breakdown item has numeric data but aiSummary is null
        for (DailySummaryResponse dailyItem : response.getDailyBreakdown()) {
            assertThat(dailyItem.getTotalSpent()).isNotNull();
            assertThat(dailyItem.getTotalTransactions()).isGreaterThan(0);
            assertThat(dailyItem.getSpendingByCategory()).isNotNull();
            assertThat(dailyItem.getExpenses()).isNotEmpty();
            assertThat(dailyItem.getAiSummary()).isNull();
        }

        // Verify exact daily items
        DailySummaryResponse day3Item = response.getDailyBreakdown().get(2);
        assertThat(day3Item.getDate()).isEqualTo(day3);
        assertThat(day3Item.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(1200.00));
        assertThat(day3Item.getWastefulCount()).isEqualTo(1);
        assertThat(day3Item.getWastefulAmount()).isEqualByComparingTo(BigDecimal.valueOf(1200.00));

        // CRITICAL CHECK: In the unoptimized code, generateDailySummary would have been called 4 times.
        // In the optimized code, it must be called exactly 0 times!
        verify(aiService, never()).generateDailySummary(any(), any(), any(), any(), anyInt());

        // Verify top-level AI methods are still invoked exactly once
        verify(aiService, times(1)).generateMonthlySummary(eq(testUser.getId()), eq(10), eq(2026), any(), any(), anyMap());
        verify(aiService, times(1)).generateSpendingTips(eq(testUser.getId()), eq(10), eq(2026), anyMap(), any(), any());
    }

    @Test
    @DisplayName("Standalone daily analytics (/api/analytics/daily) still generates AI summary as expected")
    void testDailySummary_StandaloneEndpointStillCallsAiService() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        Expense expense = Expense.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .amount(BigDecimal.valueOf(450.00))
                .description("Team lunch")
                .category(foodCategory)
                .expenseDate(date)
                .isFlaggedWasteful(false)
                .build();

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
                eq(testUser), eq(date), eq(date)))
                .thenReturn(List.of(expense));
        when(aiService.generateDailySummary(eq(testUser.getId()), eq(date), any(), anyMap(), eq(0)))
                .thenReturn("Healthy spending on food today.");

        DailySummaryResponse response = analyticsService.getDailySummary(testUser.getEmail(), date);

        assertThat(response).isNotNull();
        assertThat(response.getDate()).isEqualTo(date);
        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.valueOf(450.00));
        assertThat(response.getAiSummary()).isEqualTo("Healthy spending on food today.");

        // Standalone daily analytics MUST still invoke the AI summary service
        verify(aiService, times(1)).generateDailySummary(eq(testUser.getId()), eq(date), any(), anyMap(), eq(0));
    }

    @Test
    @DisplayName("Standalone daily analytics for empty day returns standard empty message without calling Groq")
    void testDailySummary_EmptyDay_ReturnsStandardMessageWithoutGroqCall() {
        LocalDate date = LocalDate.of(2026, 10, 16);

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(expenseRepository.findByUserAndExpenseDateBetweenOrderByExpenseDateDesc(
                eq(testUser), eq(date), eq(date)))
                .thenReturn(Collections.emptyList());

        DailySummaryResponse response = analyticsService.getDailySummary(testUser.getEmail(), date);

        assertThat(response).isNotNull();
        assertThat(response.getDate()).isEqualTo(date);
        assertThat(response.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getAiSummary()).isEqualTo("No expenses recorded for this day.");

        // When expenses list is empty, generateDailySummary is not called
        verify(aiService, never()).generateDailySummary(any(), any(), any(), any(), anyInt());
    }
}
